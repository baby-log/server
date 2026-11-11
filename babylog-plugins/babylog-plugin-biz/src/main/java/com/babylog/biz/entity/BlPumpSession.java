package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 泵奶记录（同步表，App 端 Room pump_sessions 对齐）。过期时刻按储存方式在记录时算好。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_pump_sessions")
public class BlPumpSession extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 发生时刻（epoch millis） */
    private Long startedAt;

    /** 泵出奶量（毫升） */
    private Integer amountMl;

    /** 侧别：LEFT/RIGHT/BOTH */
    private String side;

    /** 储存方式：ROOM/FRIDGE/FREEZER */
    private String storage;

    /** 过期时刻（epoch millis，按储存方式在记录时算好） */
    private Long expiresAt;

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
