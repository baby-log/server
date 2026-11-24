package com.babylog.biz.ai;

import com.babylog.ai.api.enums.AiErrorCode;
import com.babylog.ai.api.facade.IAiFacade;
import com.babylog.biz.ai.dto.AiParseRecordCmd;
import com.babylog.biz.ai.dto.AiWeeklyReportCmd;
import com.babylog.biz.ai.enums.AiFeature;
import com.babylog.biz.ai.service.AiQuotaService;
import com.babylog.biz.ai.service.BabyLogAiService;
import com.babylog.biz.ai.support.WeeklyReportAggregator;
import com.babylog.biz.ai.vo.AiRecordDraftVO;
import com.babylog.biz.ai.vo.AiWeeklyReportVO;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlChild;
import com.babylog.biz.mapper.BlChildMapper;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * V3.0 AI 服务端单测（K5 / M73，范围 B + C + D）。
 *
 * <p>覆盖 {@code 23} §五 K5 验证清单里服务端可单测的部分：
 * <ul>
 *   <li><b>降级三种</b>：无 provider / 无可用 Key ⇒ {@code 5020}；Key 失效（配置在但调用不通）⇒ {@code 5022}；
 *       都不抛 5xx、不返回空草稿、不伪造内容；</li>
 *   <li><b>草稿不落库</b>：服务只依赖 {@code BlChildMapper} 做可见性校验，
 *       物理上没有写任何同步表的入口（用 {@code never()} 锁死）；</li>
 *   <li><b>额度</b>：预占失败（{@code 8460}）时根本不发模型请求；AI 失败一律回滚；</li>
 *   <li><b>周报数字不经过模型</b>：{@code stats} 全部来自聚合器，模型只贡献 {@code narrative}；</li>
 *   <li><b>时间窗</b>：未来周 / 超过一年前 ⇒ {@code 8463}，且不消耗额度。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BabyLogAiServiceTest {

    private static final long USER = 1001L;
    private static final long CHILD = 7L;

    @Mock
    private IAiFacade aiFacade;
    @Mock
    private AiQuotaService quotaService;
    @Mock
    private WeeklyReportAggregator aggregator;
    @Mock
    private SyncAccessScopeResolver scopeResolver;
    @Mock
    private BlChildMapper childMapper;

    private BabyLogAiService service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BlChild.class);
    }

    @BeforeEach
    void setUp() {
        service = new BabyLogAiService(aiFacade, quotaService, aggregator, scopeResolver, childMapper);
        BlChild child = new BlChild();
        child.setId(CHILD);
        child.setName("筱筱");
        lenient().when(scopeResolver.resolveAccess(anyLong()))
                .thenReturn(SyncAccessScope.singleUser(USER));
        lenient().when(childMapper.selectOne(any())).thenReturn(child);
    }

    // ==================================================================
    // 1. 补录：正常路径
    // ==================================================================

    @Test
    void parseRecordReturnsDraftAndNeverWritesAnything() {
        when(quotaService.reserve(USER, AiFeature.PARSE_RECORD)).thenReturn(19);
        when(aiFacade.dynamicChat(anyString(), anyString())).thenReturn(
                "{\"recordType\":\"BOTTLE\",\"startedAt\":1737001800000,\"amountMl\":120}");

        AiRecordDraftVO draft = service.parseRecord(USER, parseCmd("10点喂了120ml奶粉"));

        assertThat(draft.getRecordType()).isEqualTo("BOTTLE");
        assertThat(draft.getAmountMl()).isEqualTo(120);
        assertThat(draft.getNeedConfirm()).isTrue();
        assertThat(draft.getQuotaRemaining()).isEqualTo(19);

        // 草稿不落库：19 张同步表一行不写，连宝宝档案也只读
        verify(childMapper, never()).insert(any(BlChild.class));
        verify(childMapper, never()).updateById(any(BlChild.class));
        verify(childMapper, never()).deleteById(anyLong());
        verify(quotaService, never()).release(anyLong(), any());
    }

    // ==================================================================
    // 2. 降级三种（5020 / 5020 / 5022）
    // ==================================================================

    @Test
    void noProviderDegradesTo5020() {
        when(quotaService.reserve(USER, AiFeature.PARSE_RECORD)).thenReturn(19);
        when(aiFacade.dynamicChat(anyString(), anyString()))
                .thenThrow(new ServiceException(AiErrorCode.AI_NOT_CONFIGURED));

        assertThatThrownBy(() -> service.parseRecord(USER, parseCmd("喂了奶")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(5020));
        verify(quotaService).release(USER, AiFeature.PARSE_RECORD); // 失败回滚，不让用户白扣
    }

    @Test
    void noAvailableKeyAlsoDegradesTo5020() {
        when(quotaService.reserve(USER, AiFeature.PARSE_RECORD)).thenReturn(19);
        when(aiFacade.dynamicChat(anyString(), anyString()))
                .thenThrow(new ServiceException(AiErrorCode.NO_AVAILABLE_KEY));

        assertThatThrownBy(() -> service.parseRecord(USER, parseCmd("喂了奶")))
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(5020));
    }

    @Test
    void deadKeyOrUnreachableEndpointDegradesTo5022() {
        when(quotaService.reserve(USER, AiFeature.PARSE_RECORD)).thenReturn(19);
        when(aiFacade.dynamicChat(anyString(), anyString()))
                .thenThrow(new ServiceException(AiErrorCode.CHAT_FAILED));

        assertThatThrownBy(() -> service.parseRecord(USER, parseCmd("喂了奶")))
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(5022));
    }

    @Test
    void unparseableModelOutputFailsWith8461AndReleasesQuota() {
        when(quotaService.reserve(USER, AiFeature.PARSE_RECORD)).thenReturn(19);
        when(aiFacade.dynamicChat(anyString(), anyString())).thenReturn("抱歉，我没听懂");

        assertThatThrownBy(() -> service.parseRecord(USER, parseCmd("喂了奶")))
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_DRAFT_UNPARSEABLE.getCode()));
        verify(quotaService).release(USER, AiFeature.PARSE_RECORD);
    }

    // ==================================================================
    // 3. 额度：超限不发模型请求
    // ==================================================================

    @Test
    void quotaExceededBlocksBeforeCallingModel() {
        when(quotaService.reserve(USER, AiFeature.PARSE_RECORD))
                .thenThrow(new ServiceException(BabyLogErrorCode.AI_QUOTA_EXCEEDED));

        assertThatThrownBy(() -> service.parseRecord(USER, parseCmd("喂了奶")))
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_QUOTA_EXCEEDED.getCode()));
        verifyNoInteractions(aiFacade); // 额度用完就别烧钱
    }

    @Test
    void childOutsideVisibleScopeIsRejectedBeforeAnyAiCall() {
        when(childMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.parseRecord(USER, parseCmd("喂了奶")))
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_CHILD_NOT_VISIBLE.getCode()));
        verifyNoInteractions(aiFacade);
        verifyNoInteractions(quotaService);
    }

    // ==================================================================
    // 4. 周报：数字来自聚合器，文案才来自模型
    // ==================================================================

    @Test
    void weeklyReportNumbersComeFromAggregatorNotModel() {
        AiWeeklyReportVO.Stats stats = new AiWeeklyReportVO.Stats();
        stats.setFeedingCount(20);
        stats.setDiaperCount(35);
        stats.setSleepMinutes(5400);
        when(quotaService.reserve(USER, AiFeature.WEEKLY_REPORT)).thenReturn(1);
        when(aggregator.aggregate(eq(USER), eq(CHILD), anyLong(), anyLong()))
                .thenReturn(new WeeklyReportAggregator.Aggregation(stats, List.of("FEEDING", "CARE")));
        when(aiFacade.dynamicChat(anyString(), anyString()))
                .thenReturn("这周喂了 20 次，尿布 35 次，睡得还不错。");

        AiWeeklyReportVO vo = service.weeklyReport(USER, weeklyCmd(null));

        assertThat(vo.getStats().getFeedingCount()).isEqualTo(20);
        assertThat(vo.getStats().getSleepMinutes()).isEqualTo(5400);
        assertThat(vo.getNarrative()).contains("20 次");
        assertThat(vo.getReadableCategories()).containsExactly("FEEDING", "CARE");
        assertThat(vo.getWeekEnd() - vo.getWeekStart()).isEqualTo(7L * 86_400_000L);
        assertThat(vo.getQuotaRemaining()).isEqualTo(1);
    }

    @Test
    void weeklyReportDegradesTo5020AndReleasesQuota() {
        when(quotaService.reserve(USER, AiFeature.WEEKLY_REPORT)).thenReturn(1);
        when(aggregator.aggregate(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(new WeeklyReportAggregator.Aggregation(new AiWeeklyReportVO.Stats(), List.of()));
        when(aiFacade.dynamicChat(anyString(), anyString()))
                .thenThrow(new ServiceException(AiErrorCode.AI_NOT_CONFIGURED));

        assertThatThrownBy(() -> service.weeklyReport(USER, weeklyCmd(null)))
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(5020));
        verify(quotaService).release(USER, AiFeature.WEEKLY_REPORT);
    }

    @Test
    void futureOrTooOldWindowIsRejectedWithoutSpendingQuota() {
        long twoYearsAgo = System.currentTimeMillis() - 730L * 86_400_000L;

        assertThatThrownBy(() -> service.weeklyReport(USER, weeklyCmd(twoYearsAgo)))
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_REPORT_WINDOW_INVALID.getCode()));
        assertThatThrownBy(() -> service.weeklyReport(USER, weeklyCmd(System.currentTimeMillis() + 86_400_000L)))
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(BabyLogErrorCode.AI_REPORT_WINDOW_INVALID.getCode()));
        verifyNoInteractions(quotaService);
        verifyNoInteractions(aiFacade);
    }

    private static AiParseRecordCmd parseCmd(String text) {
        AiParseRecordCmd cmd = new AiParseRecordCmd();
        cmd.setChildId(CHILD);
        cmd.setText(text);
        return cmd;
    }

    private static AiWeeklyReportCmd weeklyCmd(Long weekStart) {
        AiWeeklyReportCmd cmd = new AiWeeklyReportCmd();
        cmd.setChildId(CHILD);
        cmd.setWeekStart(weekStart);
        return cmd;
    }
}
