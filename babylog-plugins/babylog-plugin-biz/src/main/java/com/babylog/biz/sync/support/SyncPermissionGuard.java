package com.babylog.biz.sync.support;

import java.util.Map;

/**
 * 同步写权限校验钩子。
 *
 * <p>V2.1（M15）起真实现见 {@link MemberPermissionGuard}：经
 * {@link SyncAccessScopeResolver} 读 {@code bl_family_members}（角色/状态）与
 * {@code bl_member_permissions}（四分类 × 读写矩阵，01B 第六节）做裁决。
 * <b>写拦截（8343 拒绝）按 M15 任务书拍板留给 M16 点亮</b>，当前钩子只解析不拒绝
 * （开关见 {@code MemberPermissionGuard#WRITE_ENFORCEMENT_ENABLED}）。
 *
 * <p>调用约定：{@code SyncServiceImpl} 在归属校验（8344）与版本门禁之后、落库之前
 * 逐条调用；实现抛 {@code ServiceException(SYNC_CATEGORY_DENIED)}（8343）时由调用方
 * 收进 push 结果的 rejected 明细，<b>不要让整批失败</b>。
 */
public interface SyncPermissionGuard {

    /**
     * 校验当前账号对一条待写记录的写权限。
     *
     * @param table    目标表
     * @param userId   当前账号（已通过归属校验，即该记录属于此账号）
     * @param recordId 记录主键
     * @param payload  客户端载荷（需要 child_id 判断分类归属时从这里取，M16 按宝宝粒度裁决时用）
     */
    void checkWrite(SyncTable table, long userId, long recordId, Map<String, Object> payload);
}
