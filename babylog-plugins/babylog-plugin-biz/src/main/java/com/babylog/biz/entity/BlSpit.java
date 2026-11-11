package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 吐奶记录（同步表，App 端 Room spits 对齐）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_spits")
public class BlSpit extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 发生时刻（epoch millis） */
    private Long startedAt;

    /** 量：SMALL/MEDIUM/LARGE/PROJECTILE */
    private String amount;

    /** 时机：RIGHT_AFTER/AFTER_BURP/WITHIN_HOUR/NIGHT */
    private String timing;

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
