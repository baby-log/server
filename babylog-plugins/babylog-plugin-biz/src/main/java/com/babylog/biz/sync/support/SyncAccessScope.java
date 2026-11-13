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
 * <p><b>权限行的语义（M15 简化，M16 随「权限编辑 API + push 写拦截」收紧）</b>：
 * 显式行只做<b>并集加宽</b>，不做减法——新宝宝出现在家庭后老成员不至于失明；
 * 三个角色的默认读矩阵都是全分类（01B：CAREGIVER 健康/医疗「只读」、VIEWER「全部只读」），
 * 所以 M15 实际效果里 readableCategories 恒为全集合，管道先建好，等 OWNER 的
 * 拒绝行（M16 API）出现后立即生效为减法语义（届时改这里并补单测）。
 *
 * @param memberId         家庭成员行 id（bl_family_members.id）；无家庭为 null
 * @param role             角色（OWNER/CAREGIVER/VIEWER）；无家庭为 null
 * @param visibleUserIds   同步拉取的可见账号集合（18 张同步表的 user_id 过滤条件）
 * @param readableCategories 可读分类集合（拉取时与客户端请求的 categories 取交集）
 * @param writableCategories 可写分类集合（M15 仅供 guard 预留，写拦截 M16 点亮）
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
