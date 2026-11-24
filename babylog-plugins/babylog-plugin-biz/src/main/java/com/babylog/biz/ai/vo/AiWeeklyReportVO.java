package com.babylog.biz.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 育儿周报（K5 / M73，范围 D）。
 *
 * <p><b>结构分两半</b>：{@link Stats} 是服务端只读聚合出来的硬数字（不经过模型，可核对），
 * {@code narrative} 才是模型写的解读。<b>模型只能看到数字、不能凭空造数据</b>——
 * 这是「不夸大 AI 能力」在服务端的落点：数字错不了，文案再差也不影响事实。
 *
 * <p>读侧口径与 {@code /sync/changes} 一致（{@code user_id IN visibleUserIds} + 单宝宝），
 * 分类读权限被显式减窄时对应数字为 0 且 {@code readableCategories} 里不含该分类（客户端显示「无权限」）。
 */
@Data
@Schema(description = "育儿周报（结构化汇总 + AI 文案）")
public class AiWeeklyReportVO {

    @Schema(description = "宝宝 id（字符串下发）")
    private Long childId;

    @Schema(description = "周起始时刻（epoch millis，含；字符串下发）")
    private Long weekStart;

    @Schema(description = "周结束时刻（epoch millis，不含；字符串下发）")
    private Long weekEnd;

    @Schema(description = "AI 文案（3~5 句，不做医疗诊断、不给用药建议、不夸大）")
    private String narrative;

    @Schema(description = "结构化汇总（服务端只读聚合，未经模型）")
    private Stats stats;

    @Schema(description = "当前账号在该宝宝上可读的同步分类（FEEDING/CARE/HEALTH/MEDICAL）")
    private List<String> readableCategories;

    @Schema(description = "本次调用后的剩余额度；-1 表示该功能不限次")
    private Integer quotaRemaining;

    /** 周报硬数字（供客户端画图与人工核对） */
    @Data
    @Schema(description = "周报结构化汇总")
    public static class Stats {

        @Schema(description = "喂养次数")
        private int feedingCount;

        @Schema(description = "喂养总毫升（仅毫升口径）")
        private int feedingMl;

        @Schema(description = "亲喂次数（母乳左/右）")
        private int breastSessions;

        @Schema(description = "辅食次数")
        private int solidCount;

        @Schema(description = "尿布次数")
        private int diaperCount;

        @Schema(description = "尿次数（WET + BOTH）")
        private int diaperWet;

        @Schema(description = "便次数（SOILED + BOTH）")
        private int diaperSoiled;

        @Schema(description = "睡眠段数")
        private int sleepCount;

        @Schema(description = "睡眠总分钟（跨周段按重叠部分截断）")
        private int sleepMinutes;

        @Schema(description = "泵奶次数")
        private int pumpCount;

        @Schema(description = "泵奶总毫升")
        private int pumpMl;

        @Schema(description = "最近一次体重（kg，HEALTH 分类可读时才有）")
        private Double latestWeight;

        @Schema(description = "最近一次身高（cm，HEALTH 分类可读时才有）")
        private Double latestHeight;
    }
}
