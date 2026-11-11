package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 提醒（同步表，App 端 Room reminders 对齐）。
 * (source_type, source_id, trigger_at) 唯一。
 * 雪花化口径：child_id=0 表示聚合行哨兵（App 端原为 "all-children" 字符串，
 * V2.0 App 端迁移时替换）；source_id 保留字符串编码（如 "plan:{childId}:{疫苗名}:{剂次}"、
 * 聚合哨兵 "daily-supplement"）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_reminders")
public class BlReminder extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id（0 = 聚合全部宝宝的哨兵） */
    private Long childId;

    /** 来源类型：VACCINATION/FOLLOW_UP/VISIT/MEDICATION/PUMP_STORAGE/CUSTOM */
    private String sourceType;

    /** 来源标识（编码字符串，见类注释） */
    private String sourceId;

    /** 标题 */
    private String title;

    /** 内容 */
    private String body;

    /** 触发时刻（epoch millis，DAILY 行存下一次） */
    private Long triggerAt;

    /** 重复规则：NONE/DAILY */
    private String repeatRule;

    /** 用户总开关：0/1 */
    private Integer enabled;

    /** 上次实际送达时刻（epoch millis） */
    private Long lastFiredAt;

    /** 备注 */
    private String note;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
