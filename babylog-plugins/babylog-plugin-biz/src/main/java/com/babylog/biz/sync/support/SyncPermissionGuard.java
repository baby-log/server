package com.babylog.biz.sync.support;

import java.util.Map;

/**
 * 同步写权限校验钩子。
 *
 * <p>V2.1 真实现见 {@link MemberPermissionGuard}：按 {@link SyncAccessScope} 的
 * 可写分类矩阵裁决（M16 起拦截已点亮，8343 越权写单条拒绝）。
 *
 * <p>调用约定：{@code SyncServiceImpl} 在归属校验（8344，M16 起按家庭维度放行）与
 * 版本门禁之后、落库之前逐条调用；实现抛 {@code ServiceException(SYNC_CATEGORY_DENIED)}
 * （8343）时由调用方收进 push 结果的 rejected 明细，<b>不要让整批失败</b>
 * （越权写与 8345/8342 同属「丢弃类」语义，客户端出队不重试）。
 *
 * <p>基础设施表（category = null）不在本钩子拦截范围：family_members / member_permissions
 * 由 {@code SyncServiceImpl} 整表拒推（8350，M16 提权漏洞收口），其余四张的边界是归属校验。
 */
public interface SyncPermissionGuard {

    /**
     * 校验当前账号对一条待写记录的写权限。
     *
     * @param table    目标表
     * @param scope    当前账号的访问范围（含角色与可写分类，由调用方在本次 push 内一次解析传入）
     * @param recordId 记录主键
     * @param payload  客户端载荷（按宝宝粒度裁决时从这里取 childId，钩子签名已预留）
     */
    void checkWrite(SyncTable table, SyncAccessScope scope, long recordId, Map<String, Object> payload);
}
