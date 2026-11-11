package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 洗澡记录（同步表，App 端 Room baths 对齐）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_baths")
public class BlBath extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 发生时刻（epoch millis） */
    private Long startedAt;

    /** 水温（℃） */
    private Double waterTempC;

    /** 是否洗头（可空） */
    private Integer hairWashed;

    /** 脐带状态：INTACT/CLEANED/FALLEN_OFF */
    private String cordStatus;

    /** 备注 */
    private String note;

    /** 谁记的（成员显示名） */
    private String createdBy;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
