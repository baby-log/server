package com.babylog.biz.notice.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 站内通知（K4 / M71，服务端专用表，契约 {@code 24} ⑤ ⑥ ⑦）。
 *
 * <p><b>不进 {@code SyncTable} 枚举、不参与 {@code /sync/changes}</b>：与
 * {@code bl_media_file} / {@code bl_device} / {@code bl_family} 同口径，走独立通知端点
 * （{@code /app/babylog/notice/*}），按 {@code visibleUserIds} 拉取式下发。
 * 四端不得往 Room / SwiftData / outbox / 19 张同步表里塞通知行。
 *
 * <p><b>user_id 必填语义列</b>：服务端按 token 盖章写入，读侧
 * {@code user_id IN visibleUserIds}（家庭读侧口径，非家庭成员不可见）。
 *
 * <p><b>单人确认（24 ⑥）</b>：{@code version} 是乐观锁 CAS 的 WHERE 条件（先到者赢、
 * 后到者出 conflicts 拉取），不是同步游标；{@code serverUpdatedAt} 才是双段增量拉游标，
 * 靠 {@code ON UPDATE CURRENT_TIMESTAMP(3)} 自动推进——确认更新后 ON UPDATE 让后到者
 * 能拉到「已确认态」。两套游标绝不混用（同 {@code api/sync.js} 纪律）。
 *
 * <p><b>时间口径（24 ⑥-4）</b>：{@code detectedAt}（识别时刻，13:30）是落正式记录
 * 的业务时间；{@code confirmedAt}（确认时刻，13:50）只作审计。正式记录走客户端
 * {@code /sync/push} 到既有 19 张同步表，本表零代写、自动检测永不自动落库保持。
 *
 * <p><b>无头异常（24 ⑦）</b>：{@code eventAt} 是合成单点行的单个时间点，
 * {@code isHeadless}/{@code isAbnormal}=1 异常标记；非计时行、无配对、无 endedAt，
 * 不复用同 ID 收尾、不造第二条结束行（对 {@code 15} §二 的契约扩展而非改写）。
 *
 * <p>{@code deleted} 继承 {@link BaseDO} 的 {@code @TableLogic}；出站所有 Long 由
 * fastjson2 {@code WriteLongAsString} 序列化为字符串，TINYINT 布尔用 Integer 0/1。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_notice")
public class BlNotice extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通知类型：{@code PENDING_CONFIRM} 待确认事件 / {@code HEADLESS_ANOMALY} 无头异常 */
    private String noticeType;

    /** 所属宝宝 id */
    private Long childId;

    /** 归属用户 id（必填语义列，服务端按 token 盖章；visibleUserIds 读侧口径） */
    private Long userId;

    /** 站内消息标题（轻量） */
    private String title;

    /** 关联记录类型（事件语义，如 SLEEP / FEEDING） */
    private String recordType;

    /** 识别/检测时刻（epoch millis；落正式记录用此值，13:30） */
    private Long detectedAt;

    /** 确认时刻（epoch millis；仅审计，13:50；NULL=未确认） */
    private Long confirmedAt;

    /** 确认人 user id（先到者；NULL=未确认） */
    private Long confirmedBy;

    /** 合成单点行的单个时间点（epoch millis；仅无头异常，非计时行、无配对） */
    private Long eventAt;

    /** 无头异常分类：WAKE_WITHOUT_SLEEP / END_WITHOUT_START（NULL=非无头） */
    private String anomalyKind;

    /** 无头异常标记：0/1（TINYINT，JSON 暴露为整数） */
    @TableField("is_headless")
    private Integer isHeadless;

    /** 确认状态：0/1（单人确认原子状态机最终态；全体成员可见） */
    @TableField("is_confirmed")
    private Integer isConfirmed;

    /** 异常标记：0/1（与 isHeadless 同源，供读侧统一过滤） */
    @TableField("is_abnormal")
    private Integer isAbnormal;

    /** 乐观锁版本（单人确认 CAS WHERE 条件；非同步游标） */
    private Long version;

    /** 创建时刻（epoch millis） */
    private Long createdAt;

    /** 服务端变更游标（双段 since+sinceId 增量拉；DB ON UPDATE 自动维护，业务代码不赋值） */
    private LocalDateTime serverUpdatedAt;
}
