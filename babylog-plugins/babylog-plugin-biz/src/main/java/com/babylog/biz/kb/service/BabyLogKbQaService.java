package com.babylog.biz.kb.service;

import com.babylog.ai.api.facade.IAiFacade;
import com.babylog.ai.kb.api.dto.KbSearchResultDTO;
import com.babylog.ai.kb.api.facade.IKnowledgeBaseFacade;
import com.babylog.biz.ai.config.AiQuotaConfigReader;
import com.babylog.biz.ai.enums.AiFeature;
import com.babylog.biz.ai.service.AiQuotaService;
import com.babylog.biz.ai.support.AiErrorMapper;
import com.babylog.biz.kb.dto.KbAskCmd;
import com.babylog.biz.kb.support.KbCitationAssembler;
import com.babylog.biz.kb.support.KbDisclaimer;
import com.babylog.biz.kb.vo.KbAnswerVO;
import com.babylog.biz.kb.vo.KbCitationVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 育儿知识库 · RAG 问答（K6-S / M76，app-user 侧，按量计 {@code KB_QA}）。
 *
 * <p><b>范围 A「复用上游 RAG 骨架」</b>：检索只走 {@link IKnowledgeBaseFacade#search}，
 * 生成只走 {@link IAiFacade#dynamicChat}；本类不重造解析 / 分块 / 检索 / 重排，
 * 不依赖 ai-kb 实现层、不部署 Milvus、不引新 SDK（simple 模式由上游装配）。
 *
 * <p><b>三条红线（{@code 10} 号 §4.1）</b>：
 * <ol>
 *   <li><b>来源可追溯</b>：引用条目由 {@link KbCitationAssembler} 从上游
 *       {@code KbSearchResultDTO} 装配，fileName / docId / chunkId 三件套原样透传；</li>
 *   <li><b>全程免责</b>：{@link KbDisclaimer#TEXT} 在每个响应（含降级）恒下发，不省略；</li>
 *   <li><b>答案必带引用条目、不许裸生成</b>：检索结果为空 ⇒ <b>一次模型都不调</b>，
 *       返回 {@code answer=null}（NON_NULL 契约下缺键）+ 空 citations + {@code degraded=1}；
 *       模型返回空文本同样回滚额度并降级，绝不编造内容。</li>
 * </ol>
 *
 * <p><b>额度（龙哥 2026-11-25 拍板 + 门禁 C）</b>：{@code KB_QA} 10 次/天 DAILY 为 yml 回落基线，
 * 参数中心 {@code babylog.ai.quota.kb_qa.limit}（{@code sys_config}）实时覆盖、无需重启。
 * 计次粒度仍按家庭 × 功能（{@code scope_key}）；超限 {@code 8460} 不发模型请求；
 * 无 Key / 未配置 {@code 5020}、配置在但打不通 {@code 5022}，失败回滚预占。
 *
 * <p><b>文案红线</b>：禁用词清单只写在 {@link KbDisclaimer} 的注释里，
 * 0 处用于 UI / 接口文案；固定免责是合规物料，不抽成后台配置项。
 */
@Service
public class BabyLogKbQaService {

    private static final Logger log = LoggerFactory.getLogger(BabyLogKbQaService.class);

    /** 参数中心键（与 Flyway V2.2.21 预置行、application.yml 回落注释逐字一致） */
    public static final String KB_QA_LIMIT_KEY = "babylog.ai.quota.kb_qa.limit";

    private static final int MAX_QUESTION = 200;
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;

    private static final String SYSTEM_PROMPT = """
            你是 BabyLog（育儿记录 App）的育儿知识库问答助手。只依据下面给出的「资料片段」回答家长的问题。
            要求：
            1) 只引用资料片段里的内容，不得编造数据、不得补充片段之外的医学结论；
            2) 每个关键结论后用 [1] [2] 这样的编号标注对应资料片段；
            3) 不做疾病诊断、不给用药建议、不吓唬家长，语气平和具体；
            4) 资料片段不足以回答时，直接说「现有资料不足，建议咨询医生」，不要猜测；
            5) 纯文本输出，不要 markdown、不要标题、不要列表符号；
            6) 不要输出免责声明，服务端会统一附加。
            """;

    private final IKnowledgeBaseFacade knowledgeBaseFacade;
    private final IAiFacade aiFacade;
    private final AiQuotaService quotaService;
    private final AiQuotaConfigReader configReader;

    public BabyLogKbQaService(IKnowledgeBaseFacade knowledgeBaseFacade,
                              IAiFacade aiFacade,
                              AiQuotaService quotaService,
                              AiQuotaConfigReader configReader) {
        this.knowledgeBaseFacade = knowledgeBaseFacade;
        this.aiFacade = aiFacade;
        this.quotaService = quotaService;
        this.configReader = configReader;
    }

    /**
     * @throws ServiceException {@code 1001} 参数非法 / {@code 8460} 额度用完 /
     *                          {@code 5020} 未配置 / {@code 5022} 调用不通
     */
    public KbAnswerVO ask(long userId, KbAskCmd cmd) {
        if (cmd == null || cmd.getQuestion() == null || cmd.getQuestion().isBlank()) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        String question = cmd.getQuestion().trim();
        if (question.length() > MAX_QUESTION) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        int topK = normalizeTopK(cmd.getTopK());

        // 1) 复用上游 RAG 骨架：检索动作一次，不自己解析 / 分块 / 重排
        List<KbSearchResultDTO> hits = knowledgeBaseFacade.search(cmd.getKbId(), question, topK);
        List<KbCitationVO> citations = KbCitationAssembler.assemble(hits);

        // 2) 红线三：无可引用资料 ⇒ 不调模型、不裸生成、不抛 5xx
        if (citations.isEmpty()) {
            return KbAnswerVO.empty(KbDisclaimer.TEXT);
        }

        // 3) 预占额度（模型调用之前）；失败回滚由 catch 保证
        Integer limitOverride = resolveLimitOverride();
        int remaining = quotaService.reserve(userId, AiFeature.KB_QA, limitOverride);
        try {
            String answer = aiFacade.dynamicChat(SYSTEM_PROMPT, buildUserPrompt(question, citations));
            if (answer == null || answer.isBlank()) {
                // 模型没给出内容：不伪造、不裸生成；回滚本次预占后降级
                quotaService.release(userId, AiFeature.KB_QA);
                return KbAnswerVO.empty(KbDisclaimer.TEXT);
            }
            KbAnswerVO vo = new KbAnswerVO();
            vo.setAnswer(answer.trim());
            vo.setCitations(citations); // 红线三：答案必带引用条目
            vo.setDisclaimer(KbDisclaimer.TEXT);
            vo.setQuotaRemaining(remaining);
            vo.setDegraded(0);
            return vo;
        } catch (ServiceException e) {
            quotaService.release(userId, AiFeature.KB_QA);
            throw AiErrorMapper.translate(e);
        } catch (RuntimeException e) {
            quotaService.release(userId, AiFeature.KB_QA);
            throw e;
        }
    }

    /**
     * 参数中心实时值 → 额度上限覆盖；缺失 / 非数字 / 负数一律返回 {@code null} 回落 yml。
     * {@code 0} 是合法值（不限次、只计次），与 yml 注释口径一致。
     */
    private Integer resolveLimitOverride() {
        String raw = configReader.read(KB_QA_LIMIT_KEY);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                log.warn("[BABYLOG][kb] sys_config {} 为负数，回落 yml：{}", KB_QA_LIMIT_KEY, raw);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            log.warn("[BABYLOG][kb] sys_config {} 非数字，回落 yml：{}", KB_QA_LIMIT_KEY, raw);
            return null;
        }
    }

    private static int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    /** 拼接引用片段 + 问题；免责由服务端统一附加，不让模型自己写（避免改写合规文案） */
    private static String buildUserPrompt(String question, List<KbCitationVO> citations) {
        StringBuilder sb = new StringBuilder();
        sb.append("资料片段（每条前的 [n] 是引用编号）：\n");
        for (KbCitationVO c : citations) {
            sb.append('[').append(c.getIndex()).append("] ")
                    .append(c.getFileName() == null ? "(未标注文件名)" : c.getFileName())
                    .append('：')
                    .append(c.getExcerpt() == null ? "" : c.getExcerpt())
                    .append('\n');
        }
        sb.append("\n家长的问题：").append(question).append('\n');
        sb.append("请只依据以上资料片段回答，并在关键结论后标注引用编号。\n");
        return sb.toString();
    }
}
