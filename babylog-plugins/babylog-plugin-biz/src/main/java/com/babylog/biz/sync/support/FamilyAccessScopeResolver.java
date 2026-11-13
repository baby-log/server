package com.babylog.biz.sync.support;

import com.babylog.biz.entity.BlFamilyMember;
import com.babylog.biz.entity.BlMemberPermission;
import com.babylog.biz.family.support.FamilyPermissions;
import com.babylog.biz.mapper.BlFamilyMemberMapper;
import com.babylog.biz.mapper.BlMemberPermissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 读侧权限裁决实现（V2.1 M15）：由家庭成员行 + 权限行推导 {@link SyncAccessScope}。
 *
 * <p><b>成员判定规则</b>：只认 {@code status = ACTIVE} 的成员行——被踢出（REMOVED）
 * 的用户立即回落到「单用户」范围（只剩自己的数据），这是家庭踢出的「拦 token」形态：
 * 不删行、不吊销登录（对方仍是独立账号），靠每次拉取时重新判定生效。
 *
 * <p><b>「单用户无家庭行为零变化」回归锁</b>：无 ACTIVE 成员行 →
 * {@link SyncAccessScope#singleUser}（可见范围 {自己}、读写全开），SQL 从
 * {@code user_id = :userId} 变 {@code user_id IN ({自己})}，结果集逐行一致。
 */
@Component
public class FamilyAccessScopeResolver implements SyncAccessScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(FamilyAccessScopeResolver.class);

    private final BlFamilyMemberMapper memberMapper;
    private final BlMemberPermissionMapper permissionMapper;

    public FamilyAccessScopeResolver(BlFamilyMemberMapper memberMapper,
                                     BlMemberPermissionMapper permissionMapper) {
        this.memberMapper = memberMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public SyncAccessScope resolveAccess(long userId) {
        // MP @TableLogic 自动追加 deleted = 0；软删的成员行不参与判定
        List<BlFamilyMember> memberships = memberMapper.selectList(Wrappers.<BlFamilyMember>lambdaQuery()
                .eq(BlFamilyMember::getUserId, userId)
                .eq(BlFamilyMember::getStatus, BlFamilyMember.STATUS_ACTIVE));
        if (memberships.isEmpty()) {
            return SyncAccessScope.singleUser(userId);
        }
        if (memberships.size() > 1) {
            // create/join 的服务层守卫保证一个用户至多一条 ACTIVE 行；多条 = 脏数据，取最新加入并告警
            log.warn("[BABYLOG][family] 用户 {} 存在 {} 条 ACTIVE 成员行（应为 1），取 joined_at 最新一条",
                    userId, memberships.size());
            memberships.sort(Comparator.comparing(BlFamilyMember::getJoinedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
        }
        BlFamilyMember me = memberships.get(memberships.size() - 1);

        List<BlFamilyMember> activeMembers = memberMapper.selectList(Wrappers.<BlFamilyMember>lambdaQuery()
                .eq(BlFamilyMember::getFamilyId, me.getFamilyId())
                .eq(BlFamilyMember::getStatus, BlFamilyMember.STATUS_ACTIVE));
        List<Long> visibleUserIds = activeMembers.stream().map(BlFamilyMember::getUserId).distinct().toList();
        if (!visibleUserIds.contains(userId)) {
            // 理论不可达（me 是 ACTIVE 行）；防御脏数据时宁可漏不可多
            log.warn("[BABYLOG][family] 家庭 {} 的 ACTIVE 成员列表不含本人 {}，可见范围退化为单用户",
                    me.getFamilyId(), userId);
            return SyncAccessScope.singleUser(userId);
        }

        List<BlMemberPermission> rows = permissionMapper.selectList(Wrappers.<BlMemberPermission>lambdaQuery()
                .eq(BlMemberPermission::getMemberId, me.getId()));
        Set<SyncCategory> readable = FamilyPermissions.defaultReadable(me.getRole());
        Set<SyncCategory> writable = FamilyPermissions.defaultWritable(me.getRole());
        for (BlMemberPermission row : rows) {
            SyncCategory category = parseCategory(row.getCategory());
            if (category == null) {
                continue;
            }
            if (Integer.valueOf(1).equals(row.getCanRead())) {
                readable.add(category);
            }
            // M15：权限行只加宽不减窄（见 SyncAccessScope javadoc）；M16 权限编辑 API 上线后改「覆盖」语义
            if (Integer.valueOf(1).equals(row.getCanWrite())) {
                writable.add(category);
            }
        }
        return new SyncAccessScope(me.getId(), me.getRole(), visibleUserIds, readable, writable);
    }

    private static SyncCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SyncCategory.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("[BABYLOG][family] 权限行出现未知分类 {}，忽略该行", raw);
            return null;
        }
    }
}
