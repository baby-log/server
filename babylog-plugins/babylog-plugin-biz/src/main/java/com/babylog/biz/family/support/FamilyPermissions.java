package com.babylog.biz.family.support;

import com.babylog.biz.sync.support.SyncCategory;

import java.util.EnumSet;
import java.util.Set;

/**
 * 家庭角色 × 四分类 的默认权限矩阵（01B 第六节定稿，<b>不重新设计</b>）。
 *
 * <p>矩阵唯一事实来源：{@code FamilyAccessScopeResolver}（读侧裁决）与
 * {@code FamilyServiceImpl}（成员加入/改角色时生成默认 bl_member_permissions 行）
 * 都从这里取默认值，两处永不漂移。
 *
 * <table border="1">
 *   <tr><th>角色</th><th>FEEDING</th><th>CARE</th><th>HEALTH</th><th>MEDICAL</th></tr>
 *   <tr><td>OWNER（创建者）</td><td>读写</td><td>读写</td><td>读写</td><td>读写</td></tr>
 *   <tr><td>CAREGIVER（照护者）</td><td>读写</td><td>读写</td><td>只读</td><td>只读</td></tr>
 *   <tr><td>VIEWER（只读）</td><td>只读</td><td>只读</td><td>只读</td><td>只读</td></tr>
 * </table>
 *
 * <p>三个角色的默认读矩阵都是全分类（「健康/医疗只读」是写限制，不是读限制）。
 */
public final class FamilyPermissions {

    private FamilyPermissions() {
    }

    /** 角色默认可读分类：全部（CAREGIVER/VIEWER 的「只读」指不能写，不是看不见） */
    public static Set<SyncCategory> defaultReadable(String role) {
        return EnumSet.allOf(SyncCategory.class);
    }

    /** 角色默认可写分类：OWNER 全部；CAREGIVER 喂养+护理；VIEWER 无 */
    public static Set<SyncCategory> defaultWritable(String role) {
        return switch (role == null ? "" : role) {
            case "OWNER" -> EnumSet.allOf(SyncCategory.class);
            case "CAREGIVER" -> EnumSet.of(SyncCategory.FEEDING, SyncCategory.CARE);
            default -> EnumSet.noneOf(SyncCategory.class);
        };
    }
}
