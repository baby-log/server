package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 记录类型开关覆盖（同步表，App 端 Room record_type_settings 对齐）。
 * (child_id, record_type) 唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_record_type_settings")
public class BlRecordTypeSetting extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 记录类型（App 端 RecordType 枚举 name，21 个值） */
    private String recordType;

    /** 启用开关：0 用户显式关闭 1 启用 */
    private Integer enabled;

    /** 自定义起始月龄（天），null = 用注册表默认 */
    private Integer startAgeDays;

    /** 自定义截止月龄（天），null = 用注册表默认 */
    private Integer endAgeDays;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
