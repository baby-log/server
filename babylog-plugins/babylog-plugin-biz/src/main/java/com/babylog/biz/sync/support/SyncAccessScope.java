package com.babylog.biz.sync.support;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 一次同步访问的可见范围与权限矩阵（V2.1 M15 点亮「读」侧裁决）。
 *
 * <p>由 {@link SyncAccessScopeResolver} 按当前账号推导：
 * <ul>
 *   <li><b>无家庭</b>（未创建/未加入/被移出家庭）：可见账号集合 = {自己}，
 *       读写矩阵 = 全开——与 V2.0 单用户模型<b>行为零变化</b>（M15 回归锁）；</li>
 *   <li><b>家庭成员</b>：可见账号集合 = 家庭全部 ACTIVE 成员（读侧家庭可见），
 *       读写矩阵 = 角色默认（01B 第六节）∪ 显式 bl_member_permissions 行的授权。</li>
 * </ul>
 *
 * <p><b>权限行语义（M16 起：显式行覆盖）</b>：某分类存在任一 bl_member_permissions
 * 行时，该分类的读写裁决<b>完全由行决定</b>；无行的分类走角色默认兑底（01B 第六节）。
 * 减窄立即生效：OWNER 撤回授权（写 can_read=0 / can_write=0 行）下一次拉取/推送即被拦。
 * 行按 (member, child, category) 落，同分类跨宝宝的行取 OR（任一授权即授权）——
 * 正常数据里同分类各行一致（改角色 = 全量重写），不一致属脏数据，从宽不崩；
 * 按宝宝粒度的精确裁决（payload 已带 childId）留给后续会话。
 *
 * @param memberId         家庭成员行 id（bl_family_members.id）；无家庭为 null
 * @param role             角色（OWNER/CAREGIVER/VIEWER）；无家庭为 null
 * @param visibleUserIds   同步拉取的可见账号集合（18 张同步表的 user_id 过滤条件）
 * @param readableCategories 可读分类集合（拉取时与客户端请求的 categories 取交集）
 * @param writableCategories 可写分类集合（push 写拦截 8343 的裁决依据，M16 起生效）
 */
public record SyncAccessScope(
        Long memberId,
        String role,
        List<Long> visibleUserIds,
        Set<SyncCategory> readableCategories,
        Set<SyncCategory> writableCategories) {

    /** 无家庭：单用户模型，可见范围只有自己，读写全开（V2.0 行为回归锁） */
    public static SyncAccessScope singleUser(long userId) {
        return new SyncAccessScope(null, null, List.of(userId),
                EnumSet.allOf(SyncCategory.class), EnumSet.allOf(SyncCategory.class));
    }

    public boolean isFamilyMember() {
        return memberId != null;
    }

    public boolean isOwner() {
        return "OWNER".equals(role);
    }
}
