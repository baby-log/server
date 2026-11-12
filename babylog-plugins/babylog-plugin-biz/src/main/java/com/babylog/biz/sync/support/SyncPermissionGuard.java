package com.babylog.biz.sync.support;

import java.util.Map;

/**
 * 同步写权限校验钩子（V2.0 空实现，V2.1 点亮）。
 *
 * <p><b>为什么现在留接口而不是写判断</b>：V2.0 是单用户模型——一条记录的 {@code user_id}
 * 就是它的全部归属关系，归属校验（{@code SyncServiceImpl} 里那段 8344 判断）已经足够，
 * 此刻写「按分类查 bl_member_permissions」只会是一段永远为 true 的死代码，
 * 而它一旦写成「看起来在鉴权」的样子，V2.1 真正启用时反而没人敢改。
 *
 * <p>V2.1（家庭共享）实现要点，接手的人照这里做：
 * <ol>
 *   <li>由 {@code bl_children.family_id} 找到家庭，再按 {@code user_id} 找
 *       {@code bl_family_members.id}（member_id）；</li>
 *   <li>按「表 → 分类」映射（见 {@link SyncTable#category()}）取
 *       {@code bl_member_permissions.user_id} 那一行的 {@code can_write}；</li>
 *   <li>拒绝时抛 {@code ServiceException(SYNC_CATEGORY_DENIED)}（8343），
 *       由调用方收进 push 结果的 rejected 明细，不要让整批失败。</li>
 * </ol>
 */
public interface SyncPermissionGuard {

    /**
     * 校验当前账号对一条待写记录的写权限。
     *
     * @param table    目标表
     * @param userId   当前账号（已通过归属校验，即该记录属于此账号）
     * @param recordId 记录主键
     * @param payload  客户端载荷（需要 child_id 判断分类归属时从这里取）
     */
    void checkWrite(SyncTable table, long userId, long recordId, Map<String, Object> payload);
}
