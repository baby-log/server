package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 宝宝（同步表，App 端 Room children 对齐）。
 *
 * <p>V2.0 单用户模型：owner_user_id 生效；family_id 预留（V2.1 家庭功能点亮时回填）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_children")
public class BlChild extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属家庭（V2.0 为 null，V2.1 点亮） */
    private Long familyId;

    /** 归属用户（V2.0 单用户模型下即数据所有者） */
    private Long ownerUserId;

    /** 昵称 */
    private String name;

    /** 性别：MALE/FEMALE/UNKNOWN */
    private String gender;

    /** 生日（epoch millis，当天 00:00） */
    private Long birthday;

    /** 是否早产：0 否 1 是 */
    private Integer isPremature;

    /** 早产孕周 */
    private Integer gestationalWeeks;

    /** 出生体重（克） */
    private Integer birthWeightG;

    /** 出生身高（cm） */
    private Double birthHeightCm;

    /** 头像文件 key（App 端本地路径的远端对应物） */
    private String avatarPath;

    /** 备注 */
    private String note;

    /**
     * M57 喂养方式（宝宝档案可改，四端同步）：
     * BREAST_ONLY 纯母乳 / FORMULA_ONLY 纯配方 / MIXED 混合；null = 老数据未设置。
     * 只存枚举 name，绝不存序号（与 gender 同口径）。值域校验在客户端，服务端不强制——
     * 未知值原样下发，避免新枚举上线时旧服务端把整行拒成 8342。
     */
    private String feedingMethod;

    /** 归档标记（归档 ≠ 删除）：0 否 1 是 */
    private Integer archived;

    /** App 端创建时刻（epoch millis，业务口径） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis，业务口径） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
