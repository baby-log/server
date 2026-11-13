package com.babylog.biz.sync.support;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 写权限钩子实现（V2.1 M15 解析就绪、M16 点亮拦截）：按 {@link SyncAccessScope} 的
 * 可写分类矩阵做真实裁决，越权写抛 8343，由 {@code SyncServiceImpl} 收进单条 rejected
 * （不拖累整批；与 8345 内置产品、8342 格式非法同款「丢弃类」语义，客户端出队不重试——
 * 2026-11-13 龙哥拍板「整条拒绝」而非静默跳过）。
 *
 * <p>拦截范围（按表分两类）：
 * <ul>
 *   <li><b>业务表</b>（category 非 null 的 12 张）：非 OWNER 的家庭成员对
 *       writableCategories 之外的分类推送 → 8343。无家庭用户与 OWNER 不拦
 *       （01B 第六节：OWNER 是「不可转让的兜底，避免家庭被锁死」；无家庭 =
 *       单用户模型，读写全开，行为与 V2.0 零变化——回归锁）；</li>
 *   <li><b>基础设施表</b>（category null 的 6 张）：不在本钩子拦截——
 *       family_members / member_permissions 由 {@code SyncServiceImpl} 整表拒推
 *       （8350：这两表的行归属 = 成员自己，归属校验拦不住整行重推自我改 OWNER /
 *       翻转 canWrite），其余四张（children / record_type_settings / products /
 *       reminders）的边界是归属校验（8344，M16 起按家庭维度放行：家庭成员互相可写）。</li>
 * </ul>
 *
 * <p>矩阵来源：{@code FamilyAccessScopeResolver}——角色默认（01B 第六节）被
 * {@code bl_member_permissions} 显式行覆盖（M16 起为覆盖语义，减窄立即生效）。
 */
@Component
public class MemberPermissionGuard implements SyncPermissionGuard {

    /**
     * 写拦截开关：M15 落地时为 false（任务书拍板只解析不拒绝），M16（2026-11-13）翻 true
     * 常开。保留常量是为让 {@code MemberPermissionGuardTest} 的文档锁断言「点亮是有意为之」。
     */
    static final boolean WRITE_ENFORCEMENT_ENABLED = true;

    @Override
    public void checkWrite(SyncTable table, SyncAccessScope scope, long recordId, Map<String, Object> payload) {
        if (!WRITE_ENFORCEMENT_ENABLED) {
            return;
        }
        if (table.category() == null) {
            // 基础设施表不按分类拦截：family_members / member_permissions 整表拒推（8350）
            // 在 SyncServiceImpl 预检段，其余四张的边界是归属校验（8344）
            return;
        }
        if (!scope.isFamilyMember() || scope.isOwner()) {
            // 无家庭（单用户全开，回归锁）与 OWNER（兜底全权）
            return;
        }
        if (!scope.writableCategories().contains(table.category())) {
            throw new ServiceException(BabyLogErrorCode.SYNC_CATEGORY_DENIED);
        }
    }
}
