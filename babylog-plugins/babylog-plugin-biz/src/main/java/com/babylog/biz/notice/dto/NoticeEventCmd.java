package com.babylog.biz.notice.dto;

import lombok.Data;

import java.util.List;

/**
 * 构造检测事件命令（K4 / M71 接收侧验证用，V3 前无真实采集、K9 才出图）。
 *
 * <p><b>不是采集侧</b>：只接收结构化事件点（类型 + 单个时间点，Long 客户端发字符串），
 * 零画面、零字节、不接摄像头/麦克风。服务端判定后写 {@code bl_notice}（服务端专用表），
 * 按 {@code visibleUserIds} 全体成员可见（{@code 24} ⑤ / ⑦）。
 *
 * <p>两种构造：
 * <ul>
 *   <li>{@code noticeType=PENDING_CONFIRM} + {@code detectedAt} + {@code recordType}
 *       ⇒ 落一条「待确认事件」通知（{@code 24} ⑥ 单人确认状态机的输入）；</li>
 *   <li>{@code noticeType=HEADLESS_ANOMALY} + {@code events}
 *       ⇒ 经 {@code HeadlessAnomalyRule} 判定两条无头规则，逐条合成单点行 + 异常标记 +
 *       异常站内消息（{@code 24} ⑦）。</li>
 * </ul>
 * 自动检测永不自动落库保持：本端点只写通知表，不写正式记录、不碰 19 张同步表。
 */
@Data
public class NoticeEventCmd {

    /** 通知类型：PENDING_CONFIRM / HEADLESS_ANOMALY */
    private String noticeType;

    /** 所属宝宝 id（必填，字符串入站） */
    private Long childId;

    /** 待确认事件的识别/检测时刻（epoch millis，13:30；PENDING_CONFIRM 必填） */
    private Long detectedAt;

    /** 待确认事件的关联记录类型（如 FEEDING / SLEEP；PENDING_CONFIRM 用） */
    private String recordType;

    /** 待确认事件标题（轻量，可空则服务端给默认） */
    private String title;

    /** 构造的检测事件点（HEADLESS_ANOMALY 必填；零画面、零字节，只有类型 + 时间点） */
    private List<HeadlessAnomalyRuleEventPoint> events;

    /** 构造事件点（客户端 JSON：type + at，at 是 Long 字符串） */
    @Data
    public static class HeadlessAnomalyRuleEventPoint {
        private String type;
        private Long at;
    }
}
