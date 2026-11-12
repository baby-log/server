package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 家庭成员（同步表，App 端 Room family_members 对齐；服务端补 family_id/user_id/user_type）。
 *
 * <p>{@code user_id}（成员的用户 id）<b>继承自 {@link BlSyncDO}</b>，不在本类重复声明——
 * M6 曾在本类再写一个同名字段，导致该列被反射推导两次（insert 语句出现重复列）且 MP 的
 * 字段映射二义。V2.0 单用户模型下「同步归属」与「成员是谁」是同一件事，一个字段够用；
 * <b>V2.1 家庭共享时两者分叉</b>（同步归属转 family 维度、成员另有 member_user_id 列），
 * 届时再拆，别现在留两份。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_family_members")
public class BlFamilyMember extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 家庭 id（bl_family.id） */
    private Long familyId;

    /** 用户来源：APP / WX_MINI（小程序与 App 按两账号处理，加入同一家庭即可） */
    private String userType;

    /** 成员显示名（App 端展示） */
    private String displayName;

    /** 角色：OWNER/CAREGIVER/VIEWER */
    private String role;

    /** 状态：ACTIVE/INVITED/REMOVED */
    private String status;

    /** 加入时刻（epoch millis） */
    private Long joinedAt;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识（成员设备标识 + 同步 device_id 一列两用，沿用 App 端语义） */
    private String deviceId;
}
