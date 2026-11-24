package com.babylog.biz.notice.rule;

/**
 * 无头异常分类（K4 / M71，契约 {@code 24} ⑦-1「M39 规则引擎加两条」）。
 *
 * <p>两条规则逐字对齐 {@code 22} §二-10 / {@code 24} ⑦：
 * <ul>
 *   <li>{@link #WAKE_WITHOUT_SLEEP} 醒来无入睡：只有醒来事件、找不到配对入睡事件；</li>
 *   <li>{@link #END_WITHOUT_START} 结束无开始：只有结束事件、找不到配对开始事件。</li>
 * </ul>
 * 命中后合成单点行 + 异常标记，<b>不伪造配对、不假装完整会话</b>（{@code 24} ⑦-2）。
 */
public enum AnomalyKind {

    WAKE_WITHOUT_SLEEP("WAKE_WITHOUT_SLEEP", "醒来无入睡"),
    END_WITHOUT_START("END_WITHOUT_START", "结束无开始");

    private final String code;
    private final String label;

    AnomalyKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 落库 / 出站用的稳定字面量（与 Mini 契约纯函数逐字一致） */
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static AnomalyKind of(String raw) {
        if (raw == null) {
            return null;
        }
        for (AnomalyKind kind : values()) {
            if (kind.code.equals(raw)) {
                return kind;
            }
        }
        return null;
    }
}
