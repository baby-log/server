package com.babylog.biz.media.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.starter.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 按宝宝容量配额（K2 / M69，服务端专用表）。
 *
 * <p><b>不进 {@code SyncTable} 枚举、不参与 {@code /sync/changes}</b>（契约 {@code 24} ②-3）。
 *
 * <p>{@code used_bytes} 口径（24 ②-5 二选一）：本批选<b>由 {@code bl_media_file} 聚合</b>，
 * 不落冗余列，避免「容量计数」与「文件行」双写漂移；读侧与淘汰任务共用
 * {@code SUM(size_bytes) WHERE child_id=? AND deleted=0}。
 *
 * <p>{@code initial_bytes} 是初始容量（读侧展示用、后台不改写）；
 * {@code adjusted_bytes} 是后台动态调整后的容量（NULL / 缺省 = 未调整，生效容量回落初始容量）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_media_quota")
public class BlMediaQuota extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id（唯一，每行一宝宝） */
    private Long childId;

    /** 归属用户 id（必填语义列） */
    private Long userId;

    /** 初始容量（字节）；读侧展示用 */
    private Long initialBytes;

    /** 后台调整后容量（字节）；NULL = 未调整，生效容量回落 initialBytes */
    private Long adjustedBytes;

    /** 创建时刻（epoch millis） */
    private Long createdAt;
}
