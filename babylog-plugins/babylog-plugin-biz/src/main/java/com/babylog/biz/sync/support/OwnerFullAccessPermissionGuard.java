package com.babylog.biz.sync.support;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * V2.0 写权限实现：单用户模型下「归属即全权」，故不拦截。
 *
 * <p>存在的意义是<b>占住调用点</b>——{@code SyncServiceImpl} 每条记录都会调它一次，
 * V2.1 换成真实现时，改的是一个 {@code @Component}，而不是回头去编排逻辑里找插桩位置。
 */
@Component
public class OwnerFullAccessPermissionGuard implements SyncPermissionGuard {

    @Override
    public void checkWrite(SyncTable table, long userId, long recordId, Map<String, Object> payload) {
        // V2.0：user_id 归属校验（SyncServiceImpl）已经把越权挡在门外，这里不重复判断。
        // V2.1：改由 bl_member_permissions 判定「该成员对该分类是否有写权限」，见接口注释。
    }
}
