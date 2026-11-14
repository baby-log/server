package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 喂养记录（同步表，App 端 Room feedings 对齐）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_feedings")
public class BlFeeding extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 宝宝 id */
    private Long childId;

    /** 喂养类型：BOTTLE/BREAST_LEFT/BREAST_RIGHT/SOLID/WATER */
    private String type;

    /** 数量（奶瓶=毫升、辅食=份/克/毫升；母乳/水为 null） */
    private Integer amountMl;

    /** 数量单位：ML/GRAM/PORTION（历史行 null 按 ml 兜底） */
    private String amountUnit;

    /** 母乳单侧时长（秒），非母乳为 null */
    private Integer durationS;

    /** 产品档案 id（bl_products.id） */
    private Long productId;

    /** 品牌快照（记录时刻的产品品牌） */
    private String brandSnapshot;

    /** 规格快照 */
    private String specSnapshot;

    /** 备注 */
    private String note;

    /** 发生时刻（epoch millis） */
    private Long startedAt;

    /** 谁记的（成员显示名，业务口径；审计口径见 create_by） */
    private String createdBy;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;

    /** 计时进行中（M37，0/1；进行中的会话客户端不入队，落库的只有已完成行） */
    private Integer inProgress;

    /** 计时结束时刻（epoch millis；null = 旧数据无计时） */
    private Long endedAt;

    /** 总用时（秒，含暂停） */
    private Integer totalSeconds;

    /** 纯喂养用时（秒，减全部暂停段；喂养时长统计与均值用这个） */
    private Integer activeSeconds;

    /** 计时事件流 JSON：[{t:START|PAUSE|RESUME|END,at:epoch}] */
    private String pauseEvents;
}
