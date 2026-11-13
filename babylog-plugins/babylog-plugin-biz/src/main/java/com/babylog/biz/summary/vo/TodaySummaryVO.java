package com.babylog.biz.summary.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 今日汇总（只读聚合，小程序家庭轻入口 V2.2 第一批；App 端如需可复用） */
@Data
public class TodaySummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 今日窗口起点（epoch millis，服务端 JVM 时区当天 00:00；客户端只透传展示，禁做运算） */
    private Long dayStart;

    /** 各宝宝汇总（可见范围内、未归档的宝宝，按创建时间正序） */
    private List<ChildSummaryVO> children;

    /** 宝宝维度汇总 */
    @Data
    public static class ChildSummaryVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long childId;
        private String name;
        private String gender;
        /** 生日（epoch millis，当天 00:00） */
        private Long birthday;

        /** 喂养聚合；该分类不可读（权限行显式减窄）时为 null，客户端显示「无权限查看」 */
        private FeedingSummaryVO feeding;
        /** 尿布聚合；不可读时为 null */
        private DiaperSummaryVO diaper;
        /** 睡眠聚合；不可读时为 null */
        private SleepSummaryVO sleep;
        /** 最近动态（三类合并按发生时刻倒序，最多 10 条，「谁刚刚记了什么」） */
        private List<RecentItemVO> recent;
    }

    @Data
    public static class FeedingSummaryVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 今日喂养总次数 */
        private int count;
        /** 奶量合计（毫升；只累计单位为 ML 的记录，母乳/辅食不计入——量纲不齐不硬加） */
        private int totalMl;
        /** 母乳次数（单侧各计一次） */
        private int breastCount;
        /** 辅食次数 */
        private int solidCount;
    }

    @Data
    public static class DiaperSummaryVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 今日尿布总次数 */
        private int count;
        /** 湿（含 BOTH）次数 */
        private int wet;
        /** 脏（含 BOTH）次数 */
        private int soiled;
    }

    @Data
    public static class SleepSummaryVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 今日入睡次数 */
        private int count;
        /** 睡眠总时长（秒；进行中的一段按「到当前时刻」截断计入） */
        private long durationS;
        /** 是否有进行中的一段（未醒） */
        private boolean ongoing;
    }

    /** 最近动态条目（跨分类轻量行） */
    @Data
    public static class RecentItemVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 分类：FEEDING / DIAPER / SLEEP */
        private String category;
        /** 记录类型（喂养/尿布的 type 枚举名，睡眠固定 SLEEP） */
        private String type;
        /** 发生时刻（epoch millis） */
        private Long time;
        /** 数量（毫升；母乳时长/尿布等无量记录为 null） */
        private Integer amountMl;
        /** 谁记的（成员显示名快照，服务端直接透传） */
        private String createdBy;
    }
}
