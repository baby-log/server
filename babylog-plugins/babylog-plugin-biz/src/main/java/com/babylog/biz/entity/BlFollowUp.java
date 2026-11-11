package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 随访/复查记录（同步表，App 端 Room follow_ups 对齐）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_follow_ups")
public class BlFollowUp extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 类型：COMMUNITY_7D/R42/HEARING/HEEL_BLOOD/CUSTOM */
    private String type;

    /** 计划日期（epoch millis） */
    private Long scheduledDate;

    /** 实际日期（epoch millis） */
    private Long actualDate;

    /** 机构 */
    private String institution;

    /** 结果 */
    private String result;

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
