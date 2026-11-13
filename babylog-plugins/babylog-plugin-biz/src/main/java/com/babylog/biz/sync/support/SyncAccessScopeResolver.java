package com.babylog.biz.sync.support;

/**
 * 同步访问范围解析器（V2.1 M15）。
 *
 * <p>同步编排（{@code SyncServiceImpl#pull}）与写权限钩子（{@code SyncPermissionGuard}）
 * 都从这里拿 {@link SyncAccessScope}，而不是各自查家庭表——两处口径永远一致。
 * 抽成接口与 {@code SyncRecordStore} 同理：编排逻辑单测时 Mock，真实查询由 dev curl 兜底。
 */
public interface SyncAccessScopeResolver {

    /**
     * 解析当前账号的同步可见范围与权限矩阵。
     *
     * <p>实现约定：无 ACTIVE 家庭成员行时返回 {@code singleUser(userId)}；
     * 数据异常（一个用户多条 ACTIVE 成员行）时取 joined_at 最新一条并告警，不抛异常。
     */
    SyncAccessScope resolveAccess(long userId);
}
