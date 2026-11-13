package com.babylog.biz.sync.support;

import com.babylog.biz.entity.BlFamilyMember;
import com.babylog.biz.entity.BlMemberPermission;
import com.babylog.biz.mapper.BlFamilyMemberMapper;
import com.babylog.biz.mapper.BlMemberPermissionMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 读侧权限裁决单测（M15）：单用户回归锁 + 家庭可见范围 + REMOVED 立即失效 + 权限行加宽。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyAccessScopeResolverTest {

    private static final long ME = 1001L;
    private static final long OTHER = 2002L;

    @Mock
    private BlFamilyMemberMapper memberMapper;
    @Mock
    private BlMemberPermissionMapper permissionMapper;

    private FamilyAccessScopeResolver resolver() {
        return new FamilyAccessScopeResolver(memberMapper, permissionMapper);
    }

    private static BlFamilyMember member(long id, long familyId, long userId, String role, String status) {
        BlFamilyMember row = new BlFamilyMember();
        row.setId(id);
        row.setFamilyId(familyId);
        row.setUserId(userId);
        row.setRole(role);
        row.setStatus(status);
        row.setJoinedAt(System.currentTimeMillis());
        return row;
    }

    @Test
    void noMembershipFallsBackToSingleUserScope() {
        // M15 回归锁：无家庭用户（含被踢出后）可见范围 = {自己}，读写全开，行为与 V2.0 零变化
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        SyncAccessScope scope = resolver().resolveAccess(ME);

        assertThat(scope.memberId()).isNull();
        assertThat(scope.role()).isNull();
        assertThat(scope.visibleUserIds()).containsExactly(ME);
        assertThat(scope.readableCategories()).hasSize(4);
        assertThat(scope.writableCategories()).hasSize(4);
    }

    @Test
    void viewerMemberSeesWholeFamilyButWritesNothing() {
        BlFamilyMember me = member(9L, 5L, ME, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_ACTIVE);
        BlFamilyMember other = member(10L, 5L, OTHER, BlFamilyMember.ROLE_OWNER, BlFamilyMember.STATUS_ACTIVE);
        // 第一次 selectList：我的 ACTIVE 成员行；第二次：家庭全部 ACTIVE 成员
        when(memberMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(me))
                .thenReturn(List.of(me, other));
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        SyncAccessScope scope = resolver().resolveAccess(ME);

        assertThat(scope.memberId()).isEqualTo(9L);
        assertThat(scope.role()).isEqualTo("VIEWER");
        assertThat(scope.visibleUserIds()).containsExactlyInAnyOrder(ME, OTHER);
        // 01B 第六节：VIEWER 全部只读——读全开、写为空
        assertThat(scope.readableCategories()).hasSize(4);
        assertThat(scope.writableCategories()).isEmpty();
    }

    @Test
    void caregiverDefaultsAreReadWriteOnFeedingAndCareOnly() {
        BlFamilyMember me = member(9L, 5L, ME, BlFamilyMember.ROLE_CAREGIVER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(me))
                .thenReturn(List.of(me));
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        SyncAccessScope scope = resolver().resolveAccess(ME);

        assertThat(scope.readableCategories()).hasSize(4);
        assertThat(scope.writableCategories()).containsExactlyInAnyOrder(
                SyncCategory.FEEDING, SyncCategory.CARE);
    }

    @Test
    void removedMemberFallsBackToSingleUserImmediately() {
        // 踢出 = status REMOVED：membership 查询（只认 ACTIVE）返回空 → 单用户范围
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        SyncAccessScope scope = resolver().resolveAccess(ME);

        assertThat(scope.visibleUserIds()).containsExactly(ME);
    }

    @Test
    void permissionRowsWidenWritableAndUnknownCategoryIsIgnored() {
        BlFamilyMember me = member(9L, 5L, ME, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(me))
                .thenReturn(List.of(me));

        BlMemberPermission grant = new BlMemberPermission();
        grant.setMemberId(9L);
        grant.setCategory("MEDICAL");
        grant.setCanRead(1);
        grant.setCanWrite(1);
        BlMemberPermission dirty = new BlMemberPermission();
        dirty.setMemberId(9L);
        dirty.setCategory("NOT_A_CATEGORY"); // 脏数据：未知分类不能让裁决崩
        dirty.setCanRead(1);
        dirty.setCanWrite(1);
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant, dirty));

        SyncAccessScope scope = resolver().resolveAccess(ME);

        // VIEWER 默认写为空，被显式授权行加宽出 MEDICAL（M15：行只加宽不减窄）
        assertThat(scope.writableCategories()).containsExactly(SyncCategory.MEDICAL);
    }
}
