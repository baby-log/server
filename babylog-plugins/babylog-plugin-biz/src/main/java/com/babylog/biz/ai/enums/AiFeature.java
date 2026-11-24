package com.babylog.biz.ai.enums;

/**
 * AI 计次功能标识（K5 / M73，契约 {@code 09} 会话 R + {@code 24}）。
 *
 * <p>{@link #code()} 即 {@code bl_ai_quota.feature} 的落库值，也是
 * {@code babylog.ai.quota.features.<code>} 配置键，<b>三处必须逐字一致</b>。
 *
 * <p>{@link #HANDWRITING_IMPORT} 是 V3.1 预留位：本节点只留枚举、不实现、不配置额度
 * （未配置的功能走「不限次、只计次」的兜底，永远不会因为少了配置而把用户挡在门外）。
 */
public enum AiFeature {

    /** 自然语言补录（口述/文本 → 结构化草稿，不落库） */
    PARSE_RECORD("自然语言补录"),

    /** 育儿周报（只读既有记录 → 结构化汇总 + 文案） */
    WEEKLY_REPORT("育儿周报"),

    /** 手写拍照导入（V3.1 预留，本节点不实现） */
    HANDWRITING_IMPORT("手写拍照导入");

    private final String label;

    AiFeature(String label) {
        this.label = label;
    }

    /** 落库 / 配置键值（与枚举名一致，改名字必须同步改库与 yml） */
    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}
