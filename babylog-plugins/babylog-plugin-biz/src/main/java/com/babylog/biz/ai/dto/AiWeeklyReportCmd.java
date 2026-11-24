package com.babylog.biz.ai.dto;

import lombok.Data;

/**
 * 育儿周报入站（K5 / M73，范围 D）。
 *
 * <p><b>仅按需生成</b>（龙哥 2026-11-24 拍板）：没有定时任务、不依赖 xxl-job，
 * 用户点「生成周报」才跑，所以入站只带宝宝与可选周起始时刻。
 */
@Data
public class AiWeeklyReportCmd {

    /** 所属宝宝 id（字符串入站；须在当前账号的 visibleUserIds 家庭可见范围内） */
    private Long childId;

    /**
     * 周起始时刻（epoch millis）；缺省 = 最近 7 天（含今天）。
     * 指定时会被对齐到「该时刻所在自然日的 00:00」，窗口固定 7 天。
     */
    private Long weekStart;
}
