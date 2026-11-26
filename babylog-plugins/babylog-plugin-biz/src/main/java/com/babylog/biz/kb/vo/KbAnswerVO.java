package com.babylog.biz.kb.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * RAG 问答出参（K6-S / M76）。
 *
 * <p><b>红线三「答案必带引用条目，不允许裸生成」</b>：只要 {@code answer} 非空，
 * {@code citations} 必定非空；检索不到任何可引用资料时返回
 * {@code answer = null}（按 NON_NULL 契约该键缺省）+ 空 citations + {@code degraded = 1}，
 * <b>一次模型都不调</b>。客户端必须容忍 {@code answer} 缺键。
 *
 * <p><b>红线二</b>：{@code disclaimer} 恒为固定免责文案，任何分支都不省略。
 * {@code degraded} 用整数 1/0，不用 true/false（布尔契约）。
 */
@Data
public class KbAnswerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型答案；无可引用资料时为 null（JSON 缺省键） */
    private String answer;

    /** 引用条目；answer 非空时必定非空 */
    private List<KbCitationVO> citations = Collections.emptyList();

    /** 固定免责（10 号 §4.1 红线二） */
    private String disclaimer;

    /** 本次问答后剩余免费次数；-1 = 不限次 */
    private Integer quotaRemaining;

    /** 1 = 降级（无可引用资料，未调模型）/ 0 = 正常生成 */
    private Integer degraded;

    /** 构造「无可引用资料」的降级响应：不裸生成、不抛 5xx */
    public static KbAnswerVO empty(String disclaimer) {
        KbAnswerVO vo = new KbAnswerVO();
        vo.setAnswer(null);
        vo.setCitations(Collections.emptyList());
        vo.setDisclaimer(disclaimer);
        vo.setQuotaRemaining(-1);
        vo.setDegraded(1);
        return vo;
    }
}
