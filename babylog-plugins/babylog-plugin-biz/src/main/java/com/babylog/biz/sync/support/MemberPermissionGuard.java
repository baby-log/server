package com.babylog.biz.sync.support;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 写权限钩子实现（V2.1 M15）：读 {@code bl_member_permissions}（经
 * {@link SyncAccessScopeResolver}）做真实裁决，替换 V2.0 的 OwnerFullAccess。
 *
 * <p><b>M15 的刻意留白（任务书拍板：本会话只点亮「读」，push 越权拒绝留 M16）</b>：
 * {@link #WRITE_ENFORCEMENT_ENABLED} 为 {@code false}——矩阵解析逻辑已就位并有单测锁死
 * （{@code MemberPermissionGuardTest} 直接测 decide 语义），但 checkWrite 不抛 8343。
 * M16 点亮时改常量为 true + 打开拦截段，逻辑零改动。
 *
 * <p><b>M16 点亮时要一起处理的两件事</b>（写在这里防丢）：
 * <ol>
 *   <li>写权限判定只对新写入的归属语义完整——家庭成员编辑「别人的行」当前会被
 *       {@code SyncServiceImpl} 的归属校验拦成 8344；家庭维度的归属放行（family 内
 *       互相可写、家庭外仍 8344）与 8344 换 ID 重推的收口是同一件事（M14 P2 拍板归 V2.1）；</li>
 *   <li>权限行的「只加宽不减窄」语义改为「显式行覆盖角色默认」（见
 *       {@code SyncAccessScope} javadoc），减窄意味着 OWNER 撤回授权立即生效。</li>
 * </ol>
 */
@Component
public class MemberPermissionGuard implements SyncPermissionGuard {

    /**
     * 写拦截开关（M15 = false）。设为 true 即启用 8343 拒绝：非 OWNER 的家庭成员
     * 对 writableCategories 之外的分类推送业务记录 → 单条 rejected（不拖累整批）。
     */
    static final boolean WRITE_ENFORCEMENT_ENABLED = false;

    private final SyncAccessScopeResolver accessResolver;

    public MemberPermissionGuard(SyncAccessScopeResolver accessResolver) {
        this.accessResolver = accessResolver;
    }

    @Override
    public void checkWrite(SyncTable table, long userId, long recordId, Map<String, Object> payload) {
        if (!WRITE_ENFORCEMENT_ENABLED) {
            return;
        }
        if (table.category() == null) {
            // 基础设施表（children / family_members / member_permissions / products / reminders）
            // 不按分类拦截：归属校验（8344）已是它们的边界
            return;
        }
        SyncAccessScope scope = accessResolver.resolveAccess(userId);
        if (!scope.isFamilyMember() || scope.isOwner()) {
            // 无家庭（单用户全开）与 OWNER（兜底全权，01B：「不可转让的兜底，避免家庭被锁死」）
            return;
        }
        if (!scope.writableCategories().contains(table.category())) {
            throw new ServiceException(BabyLogErrorCode.SYNC_CATEGORY_DENIED);
        }
    }
}
