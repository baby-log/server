package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 疫苗接种记录（同步表，App 端 Room vaccinations 对齐）。
 * 注意与 bl_vaccine_schedule（后台维护的运营时间表）是两个概念：本表是用户的接种实例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_vaccinations")
public class BlVaccination extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 疫苗名称 */
    private String vaccineName;

    /** 第几剂 */
    private Integer doseNo;

    /** 计划日期（epoch millis） */
    private Long scheduledDate;

    /** 实际日期（epoch millis） */
    private Long actualDate;

    /** 状态：PLANNED/DONE/SKIPPED */
    private String status;

    /** 接种机构 */
    private String institution;

    /** 批号 */
    private String batchNo;

    /** 接种后反应 */
    private String reaction;

    /** 下次日期（epoch millis） */
    private Long nextDate;

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
