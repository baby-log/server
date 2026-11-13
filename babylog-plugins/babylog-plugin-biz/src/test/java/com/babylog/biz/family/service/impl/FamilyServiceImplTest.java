package com.babylog.biz.family.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlChild;
import com.babylog.biz.entity.BlFamily;
import com.babylog.biz.entity.BlFamilyMember;
import com.babylog.biz.entity.BlMemberPermission;
import com.babylog.biz.family.dto.FamilyCreateCmd;
import com.babylog.biz.family.dto.FamilyJoinCmd;
import com.babylog.biz.family.dto.MemberRoleCmd;
import com.babylog.biz.family.vo.FamilyVO;
import com.babylog.biz.mapper.BlChildMapper;
import com.babylog.biz.mapper.BlFamilyMapper;
import com.babylog.biz.mapper.BlFamilyMemberMapper;
import com.babylog.biz.mapper.BlMemberPermissionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.babylog.common.core.enums.error.ErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.api.facade.IUserFacade;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家庭与成员管理单测（M15）：创建/加入/改角色/踢出的语义与权限行生成。
 *
 * <p>SQL 正确性与权限行落库的真实效果由 dev 起服 curl 冒烟兜底（M15 验证记录）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyServiceImplTest {

    private static final long OWNER = 1001L;
    private static final long JOINER = 2002L;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单测 JVM 没有 MP 启动引导，LambdaUpdateWrapper 的 .set(方法引用) 需要实体的 TableInfo
        // 缓存（LambdaQueryWrapper 的 eq 惰性解析不受影响）；不初始化会在宝宝回填处抛
        // 「can not find lambda cache for this entity」
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BlChild.class);
        TableInfoHelper.initTableInfo(assistant, BlFamilyMember.class);
        TableInfoHelper.initTableInfo(assistant, BlMemberPermission.class);
    }

    @Mock
    private BlFamilyMapper familyMapper;
    @Mock
    private BlFamilyMemberMapper memberMapper;
    @Mock
    private BlMemberPermissionMapper permissionMapper;
    @Mock
    private BlChildMapper childMapper;
    @Mock
    private IUserFacade userFacade;

    private FamilyServiceImpl service() {
        return new FamilyServiceImpl(familyMapper, memberMapper, permissionMapper, childMapper, userFacade);
    }

    // ------------------------------------------------------------------
    // 创建
    // ------------------------------------------------------------------

    @Test
    void createBuildsFamilyOwnerMemberAndBackfillsChildren() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(familyMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(familyMapper.insert(any(BlFamily.class))).thenAnswer(inv -> {
            inv.getArgument(0, BlFamily.class).setId(100L);
            return 1;
        });
        when(memberMapper.insert(any(BlFamilyMember.class))).thenAnswer(inv -> {
            inv.getArgument(0, BlFamilyMember.class).setId(301L);
            return 1;
        });
        when(childMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        // 一个宝宝（family_id 由回填后查询可见——插入权限行时按 family_id 查）
        BlChild child = new BlChild();
        child.setId(77L);
        child.setFamilyId(100L);
        when(childMapper.selectList(any(Wrapper.class))).thenReturn(List.of(child));
        when(permissionMapper.insert(any(BlMemberPermission.class))).thenReturn(1);
        UserDTO user = new UserDTO();
        user.setNickname("龙哥");
        when(userFacade.getById(OWNER)).thenReturn(user);

        FamilyCreateCmd cmd = new FamilyCreateCmd();
        cmd.setName("我们家");
        FamilyVO vo = service().create(OWNER, cmd);

        assertThat(vo.getId()).isEqualTo(100L);
        assertThat(vo.getRole()).isEqualTo(BlFamilyMember.ROLE_OWNER);
        assertThat(vo.getMemberId()).isEqualTo(301L);
        assertThat(vo.getInviteCode()).hasSize(8); // OWNER 能看到邀请码

        // 成员行：OWNER + ACTIVE + 显示名取昵称
        ArgumentCaptor<BlFamilyMember> memberCaptor = ArgumentCaptor.forClass(BlFamilyMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(BlFamilyMember.ROLE_OWNER);
        assertThat(memberCaptor.getValue().getStatus()).isEqualTo(BlFamilyMember.STATUS_ACTIVE);
        assertThat(memberCaptor.getValue().getDisplayName()).isEqualTo("龙哥");
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(OWNER);

        // 宝宝归属回填：owner 名下 family_id 为空的行全部指向新家庭
        verify(childMapper).update(any(), any(Wrapper.class));

        // OWNER 默认权限行：1 宝宝 × 4 分类，全读写
        ArgumentCaptor<BlMemberPermission> permCaptor = ArgumentCaptor.forClass(BlMemberPermission.class);
        verify(permissionMapper, times(4)).insert(permCaptor.capture());
        assertThat(permCaptor.getAllValues())
                .allSatisfy(row -> {
                    assertThat(row.getMemberId()).isEqualTo(301L);
                    assertThat(row.getChildId()).isEqualTo(77L);
                    assertThat(row.getCanRead()).isEqualTo(1);
                    assertThat(row.getCanWrite()).isEqualTo(1);
                });
    }

    @Test
    void createRejectsUserAlreadyInFamily() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        FamilyCreateCmd cmd = new FamilyCreateCmd();
        cmd.setName("第二家庭");
        assertBizError(() -> service().create(OWNER, cmd), BabyLogErrorCode.MEMBER_ALREADY_JOINED);
        verify(familyMapper, never()).insert(any(BlFamily.class));
    }

    // ------------------------------------------------------------------
    // 加入
    // ------------------------------------------------------------------

    @Test
    void joinCreatesViewerMemberWithReadonlyPermissionRows() {
        BlFamily family = new BlFamily();
        family.setId(200L);
        family.setName("隔壁家庭");
        family.setInviteCode("ABCD2345");
        family.setStatus(BlFamily.STATUS_ACTIVE);
        when(familyMapper.selectOne(any(Wrapper.class))).thenReturn(family);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(memberMapper.insert(any(BlFamilyMember.class))).thenAnswer(inv -> {
            inv.getArgument(0, BlFamilyMember.class).setId(302L);
            return 1;
        });
        BlChild child = new BlChild();
        child.setId(77L);
        when(childMapper.selectList(any(Wrapper.class))).thenReturn(List.of(child));
        when(permissionMapper.insert(any(BlMemberPermission.class))).thenReturn(1);
        UserDTO user = new UserDTO();
        user.setUsername("mom");
        when(userFacade.getById(JOINER)).thenReturn(user);

        FamilyJoinCmd cmd = new FamilyJoinCmd();
        cmd.setInviteCode(" abcd2345 "); // 小写 + 空格：归一化后应命中
        FamilyVO vo = service().join(JOINER, cmd);

        assertThat(vo.getId()).isEqualTo(200L);
        assertThat(vo.getRole()).isEqualTo(BlFamilyMember.ROLE_VIEWER);
        assertThat(vo.getInviteCode()).isNull(); // 非 OWNER 看不到邀请码

        ArgumentCaptor<BlMemberPermission> permCaptor = ArgumentCaptor.forClass(BlMemberPermission.class);
        verify(permissionMapper, times(4)).insert(permCaptor.capture());
        // VIEWER 默认：全部只读（01B 第六节「新成员加入默认 VIEWER（最小权限）」）
        assertThat(permCaptor.getAllValues())
                .allSatisfy(row -> {
                    assertThat(row.getCanRead()).isEqualTo(1);
                    assertThat(row.getCanWrite()).isEqualTo(0);
                    assertThat(row.getUserId()).isEqualTo(JOINER); // 归属 = 该成员（拉得到自己的矩阵）
                });

        ArgumentCaptor<BlFamilyMember> memberCaptor = ArgumentCaptor.forClass(BlFamilyMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getDisplayName()).isEqualTo("mom"); // 无昵称回退用户名
    }

    @Test
    void joinRejectsInvalidCodeWithoutExistenceSignal() {
        when(familyMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        FamilyJoinCmd cmd = new FamilyJoinCmd();
        cmd.setInviteCode("ZZZZ9999");
        // 码错/家庭解散/软删统一 8301
        assertBizError(() -> service().join(JOINER, cmd), BabyLogErrorCode.INVITE_CODE_INVALID);
    }

    @Test
    void joinRejectsUserAlreadyInFamily() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        FamilyJoinCmd cmd = new FamilyJoinCmd();
        cmd.setInviteCode("ABCD2345");
        assertBizError(() -> service().join(JOINER, cmd), BabyLogErrorCode.MEMBER_ALREADY_JOINED);
    }

    // ------------------------------------------------------------------
    // 我的家庭
    // ------------------------------------------------------------------

    @Test
    void myFamilyReturns8300WhenUserHasNoFamily() {
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertBizError(() -> service().myFamily(OWNER), BabyLogErrorCode.FAMILY_NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // 改角色
    // ------------------------------------------------------------------

    @Test
    void changeRoleUpdatesPermissionRowsInPlace() {
        BlFamilyMember me = member(301L, 200L, OWNER, BlFamilyMember.ROLE_OWNER, BlFamilyMember.STATUS_ACTIVE);
        BlFamilyMember target = member(302L, 200L, JOINER, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(me));
        when(memberMapper.selectById(302L)).thenReturn(target);
        when(memberMapper.updateById(any(BlFamilyMember.class))).thenReturn(1);
        // 已有 4 行（VIEWER：全部只读），同一宝宝 × 四分类
        BlChild child = new BlChild();
        child.setId(77L);
        child.setFamilyId(200L);
        when(childMapper.selectList(any(Wrapper.class))).thenReturn(List.of(child));
        List<BlMemberPermission> oldRows = List.of(
                permRow(900L, 302L, 77L, "FEEDING", 0),
                permRow(901L, 302L, 77L, "CARE", 0),
                permRow(902L, 302L, 77L, "HEALTH", 0),
                permRow(903L, 302L, 77L, "MEDICAL", 0));
        // 模拟 SELECT 出来的行：游标列非空（M18 bug 复现前提——不置空则 ON UPDATE 不触发）
        oldRows.forEach(row -> row.setServerUpdatedAt(java.time.LocalDateTime.of(2026, 11, 13, 10, 0)));
        target.setServerUpdatedAt(java.time.LocalDateTime.of(2026, 11, 13, 10, 0));
        // 第一次 selectList：regenerate 读已有权限行；同一次调用内 insertDefault 再读一次（同一结果）
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(oldRows);
        when(permissionMapper.updateById(any(BlMemberPermission.class))).thenReturn(1);

        MemberRoleCmd cmd = new MemberRoleCmd();
        cmd.setRole("CAREGIVER");
        service().changeRole(OWNER, 302L, cmd);

        // M18 回归锁：updateById 前游标列必须置空——非空会被 MP NOT_NULL 策略显式 SET 回旧值，
        // ON UPDATE CURRENT_TIMESTAMP(3) 不触发，改角色后增量拉取 0 行（M17 真机踩坑）
        ArgumentCaptor<BlFamilyMember> memberCaptor = ArgumentCaptor.forClass(BlFamilyMember.class);
        verify(memberMapper).updateById(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getServerUpdatedAt()).isNull();
        // 旧行原地更新：只有 canWrite 变化的两行（FEEDING/CARE 0→1）被 updateById，
        // 行 id 不变（uk(member_id, child_id, category) 不含 deleted，软删重插会撞键——dev 冒烟实测）
        ArgumentCaptor<BlMemberPermission> permCaptor = ArgumentCaptor.forClass(BlMemberPermission.class);
        verify(permissionMapper, times(2)).updateById(permCaptor.capture());
        assertThat(permCaptor.getAllValues())
                .extracting(BlMemberPermission::getCategory, BlMemberPermission::getCanWrite)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("FEEDING", 1),
                        org.assertj.core.groups.Tuple.tuple("CARE", 1));
        // 权限行同样：游标列置空（增量可见的前提）
        assertThat(permCaptor.getAllValues())
                .allSatisfy(row -> assertThat(row.getServerUpdatedAt()).isNull());
        // 已有行全部覆盖（宝宝 × 四分类），无插无删
        verify(permissionMapper, never()).insert(any(BlMemberPermission.class));
        verify(permissionMapper, never()).deleteById(anyLong());
    }

    private static BlMemberPermission permRow(long id, long memberId, long childId, String category, int canWrite) {
        BlMemberPermission row = new BlMemberPermission();
        row.setId(id);
        row.setMemberId(memberId);
        row.setChildId(childId);
        row.setCategory(category);
        row.setCanRead(1);
        row.setCanWrite(canWrite);
        return row;
    }

    @Test
    void changeRoleRejectsNonOwner() {
        BlFamilyMember me = member(302L, 200L, JOINER, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(me));

        MemberRoleCmd cmd = new MemberRoleCmd();
        cmd.setRole("CAREGIVER");
        assertBizError(() -> service().changeRole(JOINER, 302L, cmd), BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);
    }

    @Test
    void changeRoleRejectsSelfAndForeignMember() {
        BlFamilyMember me = member(301L, 200L, OWNER, BlFamilyMember.ROLE_OWNER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(me));
        // 自己（OWNER 行）也进 selectById：自改自踢要在归属判断之后命中「不可转让」
        when(memberMapper.selectById(301L)).thenReturn(me);
        // 目标行存在但属于别的家庭：统一 8302（不给存在性信号）
        BlFamilyMember foreign = member(999L, 999L, 42L, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectById(999L)).thenReturn(foreign);

        MemberRoleCmd cmd = new MemberRoleCmd();
        cmd.setRole("CAREGIVER");
        assertBizError(() -> service().changeRole(OWNER, 301L, cmd), BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);
        assertBizError(() -> service().changeRole(OWNER, 999L, cmd), BabyLogErrorCode.NOT_FAMILY_MEMBER);
    }

    // ------------------------------------------------------------------
    // 踢出
    // ------------------------------------------------------------------

    @Test
    void kickMarksRemovedWithoutDeletingRow() {
        BlFamilyMember me = member(301L, 200L, OWNER, BlFamilyMember.ROLE_OWNER, BlFamilyMember.STATUS_ACTIVE);
        BlFamilyMember target = member(302L, 200L, JOINER, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_ACTIVE);
        // SELECT 出来的行游标非空：kick 后进 updateById 的实体必须置空（M18 回归锁，同 changeRole）
        target.setServerUpdatedAt(java.time.LocalDateTime.of(2026, 11, 13, 10, 0));
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(me));
        when(memberMapper.selectById(302L)).thenReturn(target);
        when(memberMapper.updateById(any(BlFamilyMember.class))).thenReturn(1);

        service().kick(OWNER, 302L);

        ArgumentCaptor<BlFamilyMember> captor = ArgumentCaptor.forClass(BlFamilyMember.class);
        verify(memberMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BlFamilyMember.STATUS_REMOVED);
        assertThat(captor.getValue().getServerUpdatedAt()).isNull();
        // 行不物理删：同步通道要能看到「谁被移除了」；权限行也不动（裁决只认 ACTIVE）
        verify(memberMapper, never()).deleteById(anyLong());
        verify(permissionMapper, never()).deleteById(anyLong());
    }

    @Test
    void kickIsIdempotentForAlreadyRemovedMember() {
        BlFamilyMember me = member(301L, 200L, OWNER, BlFamilyMember.ROLE_OWNER, BlFamilyMember.STATUS_ACTIVE);
        BlFamilyMember target = member(302L, 200L, JOINER, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_REMOVED);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(me));
        when(memberMapper.selectById(302L)).thenReturn(target);

        service().kick(OWNER, 302L);

        verify(memberMapper, never()).updateById(any(BlFamilyMember.class));
    }

    @Test
    void kickRejectsSelfAndNonOwner() {
        BlFamilyMember me = member(301L, 200L, OWNER, BlFamilyMember.ROLE_OWNER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(me));
        when(memberMapper.selectById(301L)).thenReturn(me);
        assertBizError(() -> service().kick(OWNER, 301L), BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);

        BlFamilyMember viewer = member(302L, 200L, JOINER, BlFamilyMember.ROLE_VIEWER, BlFamilyMember.STATUS_ACTIVE);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(viewer));
        assertBizError(() -> service().kick(JOINER, 301L), BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);
    }

    // ------------------------------------------------------------------

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

    private static void assertBizError(org.junit.jupiter.api.function.Executable action, ErrorCode expected) {
        assertThatThrownBy(action::execute)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(expected.getCode()));
    }
}
