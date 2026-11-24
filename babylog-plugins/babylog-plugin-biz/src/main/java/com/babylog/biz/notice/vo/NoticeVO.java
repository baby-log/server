package com.babylog.biz.notice.vo;

import lombok.Data;

/**
 * 站内通知 VO（K4 / M71，App / 小程序端点出站）。
 *
 * <p><b>全局契约坑</b>：所有 Long 字段由 {@code starter-core} fastjson2
 * {@code WriteLongAsString} 自动序列化为字符串（id / childId / userId / detectedAt /
 * confirmedAt / eventAt / version / createdAt / nextSince 无一例外），本 VO 沿用项目惯例
 * 直接声明 Long；TINYINT 布尔（isHeadless / isConfirmed / isAbnormal）用 Integer 0/1 输出，
 * 绝不发 true/false；null 键全局缺省（NON_NULL），四端容忍缺键。
 *
 * <p><b>服务端专用表</b>：本 VO 是 {@code bl_notice} 行的出站镜像，不进 {@code SyncTable}、
 * 不参与 {@code /sync/changes}，走 {@code /app/babylog/notice/*} 独立端点。
 */
@Data
public class NoticeVO {

    private Long id;
    private Long childId;
    private Long userId;
    /** PENDING_CONFIRM / HEADLESS_ANOMALY */
    private String noticeType;
    private String title;
    private String recordType;
    /** 识别/检测时刻（13:30；落正式记录用此值） */
    private Long detectedAt;
    /** 确认时刻（13:50；仅审计） */
    private Long confirmedAt;
    /** 确认人 user id（先到者） */
    private Long confirmedBy;
    /** 合成单点行的单个时间点（仅无头异常；非计时行、无配对） */
    private Long eventAt;
    /** 无头异常分类 WAKE_WITHOUT_SLEEP / END_WITHOUT_START */
    private String anomalyKind;
    /** 无头异常标记 0/1 */
    private Integer isHeadless;
    /** 确认状态 0/1 */
    private Integer isConfirmed;
    /** 异常标记 0/1 */
    private Integer isAbnormal;
    /** 乐观锁版本（单人确认 CAS 期望值） */
    private Long version;
    /** 创建时刻（epoch millis） */
    private Long createdAt;
    /** 增量拉游标（server_updated_at 的 epoch millis；客户端原样回传） */
    private Long serverUpdatedAt;
}
