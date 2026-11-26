package com.babylog.biz.kb.service;

import com.babylog.ai.api.enums.AiErrorCode;
import com.babylog.ai.api.facade.IAiFacade;
import com.babylog.ai.kb.api.dto.KbSearchResultDTO;
import com.babylog.ai.kb.api.facade.IKnowledgeBaseFacade;
import com.babylog.biz.ai.config.AiQuotaConfigReader;
import com.babylog.biz.ai.enums.AiFeature;
import com.babylog.biz.ai.service.AiQuotaService;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.kb.dto.KbAskCmd;
import com.babylog.biz.kb.support.KbDisclaimer;
import com.babylog.biz.kb.vo.KbAnswerVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 育儿知识库 RAG 问答单测（K6-S / M76）。
 *
 * <p>锁死四条不可回退的纪律：
 * <ul>
 *   <li>红线三：零引用 ⇒ 一次模型都不调、不占额度、不裸生成；</li>
 *   <li>红线二：每个响应（含降级）都带固定免责；</li>
 *   <li>红线一：引用条目保留 fileName/docId/chunkId 溯源三件套；</li>
 *   <li>额度：超限 8460 不发模型请求、模型失败回滚、参数中心值透传、无 Key 5020。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BabyLogKbQaServiceTest {

    private static final long USER = 1001L;

    @Mock
    private IKnowledgeBaseFacade kbFacade;
    @Mock
    private IAiFacade aiFacade;
    @Mock
    private AiQuotaService quotaService;
    @Mock
    private AiQuotaConfigReader configReader;

    private BabyLogKbQaService service;

    @BeforeEach
    void setUp() {
        service = new BabyLogKbQaService(kbFacade, aiFacade, quotaService, configReader);
    }

    // ------------------------------------------------------------------
    // 1. 红线三：无可引用资料不裸生成
    // ------------------------------------------------------------------

    @Test
    void noCitationsMeansNoModelCallAndNoQuotaReserve() {
        when(kbFacade.search(any(), anyString(), anyInt())).thenReturn(List.of());

        KbAnswerVO vo = service.ask(USER, cmd("宝宝发烧怎么办"));

        assertThat(vo.getDegraded()).isEqualTo(1);
        assertThat(vo.getCitations()).isEmpty();
        assertThat(vo.getAnswer()).isNull();
        assertThat(vo.getDisclaimer()).isEqualTo(KbDisclaimer.TEXT);
        verify(aiFacade, never()).dynamicChat(anyString(), anyString());
        verify(quotaService, never()).reserve(anyLong(), any(), any());
    }

    @Test
    void blankModelAnswerRollsBackQuotaAndDegrades() {
        when(kbFacade.search(any(), anyString(), anyInt())).thenReturn(List.of(hit("资料内容", 7L, 11L)));
        when(quotaService.reserve(eq(USER), eq(AiFeature.KB_QA), any())).thenReturn(9);
        when(aiFacade.dynamicChat(anyString(), anyString())).thenReturn("   ");

        KbAnswerVO vo = service.ask(USER, cmd("怎么拍嗝"));

        assertThat(vo.getDegraded()).isEqualTo(1);
        assertThat(vo.getAnswer()).isNull();
        assertThat(vo.getCitations()).isEmpty(); // 降级响应不夹带无答案的引用
        assertThat(vo.getDisclaimer()).isEqualTo(KbDisclaimer.TEXT);
        verify(quotaService).release(USER, AiFeature.KB_QA);
    }

    // ------------------------------------------------------------------
    // 2. 正常生成：答案必带引用 + 溯源三件套 + 固定免责
    // ------------------------------------------------------------------

    @Test
    void answerAlwaysCarriesCitationsDisclaimerAndTraceFields() {
        when(kbFacade.search(any(), anyString(), anyInt())).thenReturn(List.of(hit("拍嗝要点摘录", 7L, 11L)));
        when(configReader.read(BabyLogKbQaService.KB_QA_LIMIT_KEY)).thenReturn(null); // 回落 yml
        when(quotaService.reserve(eq(USER), eq(AiFeature.KB_QA), isNull())).thenReturn(9);
        when(aiFacade.dynamicChat(anyString(), anyString())).thenReturn("按资料建议竖抱拍背 [1]");

        KbAnswerVO vo = service.ask(USER, cmd("怎么拍嗝"));

        assertThat(vo.getAnswer()).isNotBlank();
        assertThat(vo.getCitations()).hasSize(1);
        assertThat(vo.getCitations().get(0).getIndex()).isEqualTo(1);
        assertThat(vo.getCitations().get(0).getFileName()).isEqualTo("权威育儿资料.pdf");
        assertThat(vo.getCitations().get(0).getDocId()).isEqualTo(7L);
        assertThat(vo.getCitations().get(0).getChunkId()).isEqualTo(11L);
        assertThat(vo.getDisclaimer()).isEqualTo(KbDisclaimer.TEXT);
        assertThat(vo.getQuotaRemaining()).isEqualTo(9);
        assertThat(vo.getDegraded()).isZero();
        verify(quotaService).reserve(USER, AiFeature.KB_QA, null);
    }

    // ------------------------------------------------------------------
    // 3. 门禁 C：参数中心实时额度覆盖 / 非法回落
    // ------------------------------------------------------------------

    @Test
    void sysConfigLimitIsPassedToReserve() {
        when(kbFacade.search(any(), anyString(), anyInt())).thenReturn(List.of(hit("资料", 7L, 11L)));
        when(configReader.read(BabyLogKbQaService.KB_QA_LIMIT_KEY)).thenReturn("25");
        when(quotaService.reserve(eq(USER), eq(AiFeature.KB_QA), eq(25))).thenReturn(24);
        when(aiFacade.dynamicChat(anyString(), anyString())).thenReturn("答案 [1]");

        service.ask(USER, cmd("怎么拍嗝"));

        verify(quotaService).reserve(USER, AiFeature.KB_QA, 25);
    }

    @Test
    void invalidSysConfigFallsBackToYml() {
        when(kbFacade.search(any(), anyString(), anyInt())).thenReturn(List.of(hit("资料", 7L, 11L)));
        when(configReader.read(BabyLogKbQaService.KB_QA_LIMIT_KEY)).thenReturn("abc");
        when(quotaService.reserve(eq(USER), eq(AiFeature.KB_QA), isNull())).thenReturn(9);
        when(aiFacade.dynamicChat(anyString(), anyString())).thenReturn("答案 [1]");

        service.ask(USER, cmd("怎么拍嗝"));

        verify(quotaService).reserve(USER, AiFeature.KB_QA, null);
    }

    // ------------------------------------------------------------------
    // 4. 额度与降级边界
    // ------------------------------------------------------------------

    @Test
    void quotaExceededNeverCallsModel() {
        when(kbFacade.search(any(), anyString(), anyInt())).thenReturn(List.of(hit("资料", 7L, 11L)));
        when(quotaService.reserve(eq(USER), eq(AiFeature.KB_QA), any()))
                .thenThrow(new ServiceException(BabyLogErrorCode.AI_QUOTA_EXCEEDED));

        assertThatThrownBy(() -> service.ask(USER, cmd("怎么拍嗝")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_QUOTA_EXCEEDED.getCode()));
        verify(aiFacade, never()).dynamicChat(anyString(), anyString());
        verify(quotaService, never()).release(anyLong(), any());
    }

    @Test
    void aiNotConfiguredIsTranslatedTo5020AndQuotaReleased() {
        when(kbFacade.search(any(), anyString(), anyInt())).thenReturn(List.of(hit("资料", 7L, 11L)));
        when(quotaService.reserve(eq(USER), eq(AiFeature.KB_QA), any())).thenReturn(9);
        when(aiFacade.dynamicChat(anyString(), anyString()))
                .thenThrow(new ServiceException(AiErrorCode.AI_NOT_CONFIGURED));

        assertThatThrownBy(() -> service.ask(USER, cmd("怎么拍嗝")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(AiErrorCode.AI_NOT_CONFIGURED.getCode()));
        verify(quotaService).release(USER, AiFeature.KB_QA);
    }

    @Test
    void blankQuestionRejectedBeforeAnySearch() {
        assertThatThrownBy(() -> service.ask(USER, cmd("   ")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(GlobalErrorCode.PARAM_INVALID.getCode()));
        verifyNoInteractions(kbFacade, aiFacade, quotaService);
    }

    // ------------------------------------------------------------------

    private static KbAskCmd cmd(String question) {
        KbAskCmd c = new KbAskCmd();
        c.setQuestion(question);
        return c;
    }

    private static KbSearchResultDTO hit(String content, Long docId, Long chunkId) {
        KbSearchResultDTO d = new KbSearchResultDTO();
        d.setContent(content);
        d.setFileName("权威育儿资料.pdf");
        d.setDocId(docId);
        d.setChunkId(chunkId);
        d.setKbId(1L);
        d.setScore(0.82);
        return d;
    }
}
