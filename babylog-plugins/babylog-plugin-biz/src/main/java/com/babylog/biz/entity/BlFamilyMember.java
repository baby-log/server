package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 家庭成员（同步表，App 端 Room family_members 对齐；服务端补 family_id/user_id/user_type）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_family_members")
public class BlFamilyMember extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 家庭 id（bl_family.id） */
    private Long familyId;

    /** 用户 id（sys_user.id，账号体系复用 plugin-system） */
    private Long userId;

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
