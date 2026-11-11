package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用药/补剂记录（同步表，App 端 Room medications 对齐）。ended_at 为 null 表示还在用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_medications")
public class BlMedication extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 类别：SUPPLEMENT/MEDICINE */
    private String category;

    /** 药名/补剂名 */
    private String name;

    /** 产品档案 id（bl_products.id） */
    private Long productId;

    /** 品牌快照 */
    private String brandSnapshot;

    /** 规格快照 */
    private String specSnapshot;

    /** 剂量（文本） */
    private String dose;

    /** 单位 */
    private String unit;

    /** 途径（口服/外用/滴入） */
    private String route;

    /** 开始时刻（epoch millis） */
    private Long startedAt;

    /** 疗程结束时刻（epoch millis），null = 还在用 */
    private Long endedAt;

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
