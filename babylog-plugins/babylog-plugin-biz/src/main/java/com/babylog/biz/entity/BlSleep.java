package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 睡眠记录（同步表，App 端 Room sleeps 对齐）。ended_at 为 null 表示进行中。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_sleeps")
public class BlSleep extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 入睡时刻（epoch millis，醒来时可改） */
    private Long startedAt;

    /** 醒来时刻（epoch millis），null = 进行中 */
    private Long endedAt;

    /** 睡觉地点（枚举 name，自由列） */
    private String place;

    /** 谁记的（成员显示名） */
    private String createdBy;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
