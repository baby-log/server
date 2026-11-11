package com.babylog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 成员分类权限（同步表，App 端 Room member_permissions 对齐；01B 权限模型四分类×读写）。
 * (member_id, child_id, category) 唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bl_member_permissions")
public class BlMemberPermission extends BlSyncDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 成员 id（bl_family_members.id） */
    private Long memberId;

    /** 宝宝 id */
    private Long childId;

    /** 分类：FEEDING/CARE/HEALTH/MEDICAL */
    private String category;

    /** 可读：0/1 */
    private Integer canRead;

    /** 可写：0/1 */
    private Integer canWrite;

    /** App 端创建时刻（epoch millis） */
    private Long createdAt;

    /** App 端最后更新时刻（epoch millis） */
    private Long updatedAt;

    /** 来源设备标识 */
    private String deviceId;
}
