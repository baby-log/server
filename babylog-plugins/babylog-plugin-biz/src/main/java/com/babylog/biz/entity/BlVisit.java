package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 就医记录（同步表，App 端 Room visits 对齐）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_visits")
public class BlVisit extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 就诊时刻（epoch millis） */
    private Long visitedAt;

    /** 医院 */
    private String hospital;

    /** 科室 */
    private String department;

    /** 医生 */
    private String doctor;

    /** 主诉 */
    private String chiefComplaint;

    /** 诊断 */
    private String diagnosis;

    /** 处方 */
    private String prescription;

    /** 费用（元） */
    private Double cost;

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
