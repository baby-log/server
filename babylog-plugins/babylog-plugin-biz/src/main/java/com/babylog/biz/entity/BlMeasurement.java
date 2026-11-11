package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 测量记录（同步表，App 端 Room measurements 对齐）：体重/身高/头围/体温/黄疸。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_measurements")
public class BlMeasurement extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 测量类型：WEIGHT/HEIGHT/HEAD/TEMP/JAUNDICE */
    private String type;

    /** 数值（黄疸可只记范围不记数值，故可空） */
    private Double value;

    /** 测量方式（体温→部位 name、黄疸→范围 name，自由文本列） */
    private String method;

    /** 测量时刻（epoch millis） */
    private Long measuredAt;

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
