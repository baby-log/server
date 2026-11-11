package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 尿布记录（同步表，App 端 Room diapers 对齐）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_diapers")
public class BlDiaper extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 尿布类型：WET/SOILED/BOTH */
    private String type;

    /** 大便性状（枚举 name，自由列） */
    private String consistency;

    /** 大便颜色（枚举 name，自由列） */
    private String color;

    /** 发生时刻（epoch millis） */
    private Long startedAt;

    /** 谁记的（成员显示名） */
    private String createdBy;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
