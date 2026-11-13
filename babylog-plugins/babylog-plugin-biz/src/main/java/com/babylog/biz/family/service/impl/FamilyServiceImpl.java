package com.babylog.biz.family.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlChild;
import com.babylog.biz.entity.BlFamily;
import com.babylog.biz.entity.BlFamilyMember;
import com.babylog.biz.entity.BlMemberPermission;
import com.babylog.biz.family.dto.FamilyCreateCmd;
import com.babylog.biz.family.dto.FamilyJoinCmd;
import com.babylog.biz.family.dto.MemberRoleCmd;
import com.babylog.biz.family.service.FamilyService;
import com.babylog.biz.family.support.FamilyPermissions;
import com.babylog.biz.family.support.InviteCodes;
import com.babylog.biz.family.vo.FamilyMemberVO;
import com.babylog.biz.family.vo.FamilyVO;
import com.babylog.biz.mapper.BlChildMapper;
import com.babylog.biz.mapper.BlFamilyMapper;
import com.babylog.biz.mapper.BlFamilyMemberMapper;
import com.babylog.biz.mapper.BlMemberPermissionMapper;
import com.babylog.biz.sync.support.SyncCategory;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.api.facade.IUserFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 家庭与成员管理实现（M15）。
 *
 * <p><b>几个必须记住的口径</b>：
 * <ul>
 *   <li><b>一个用户至多一条 ACTIVE 成员行</b>：create/join 双守卫（8303）。历史 REMOVED 行
 *       保留（同步可见「谁被移除了」），不参与任何裁决；</li>
 *   <li><b>踢出 = status 置 REMOVED，不删行、不吊销 token</b>（与设备管理 kick 同思路：
 *       拦的是后续访问——读侧裁决只认 ACTIVE，被踢成员下次拉取立即回落单用户范围；
 *       对方仍是独立账号，自己的数据不受影响）；</li>
 *   <li><b>权限行随成员生命周期生成</b>：加入（默认角色）/改角色时按 01B 角色默认矩阵
 *       对家庭全部宝宝 × 四分类落 {@code bl_member_permissions} 行（归属 user_id = 该成员，
 *       其 App 端能拉到自己的权限矩阵）；改角色时<b>原地更新</b>已有行的 can_write（行 id
 *       不变、version+1，同步可见「权限值变了」）——不能软删重建：唯一键
 *       (member_id, child_id, category) 不含 deleted，软删行仍占键，重插必撞 DuplicateKey
 *       （dev 冒烟实测））；</li>
 *   <li><b>退出/解散（M18）与踢出同语义</b>：只动成员行 status（REMOVED），不吊销 token、
 *       权限行不动、幂等靠「ACTIVE 行不存在 → 8300」。OWNER 不可退出（8305，防家庭锁死），
 *       只能解散；解散 = 家庭行 DISSOLVED（旧码 join 8301）+ 全员 REMOVED +
 *       宝宝 family_id 清空（与 create 回填对称，支持解散后重建家庭——2026-11-13 龙哥拍板）；</li>
 *   <li><b>updateById 前必须置空 serverUpdatedAt</b>（M18）：同步表实体从 SELECT 出来游标列非空，
 *       MP NOT_NULL 策略会把旧值写回 SET 段 → MySQL ON UPDATE CURRENT_TIMESTAMP(3) 不触发 →
 *       行变更对增量拉取不可见。changeRole/kick/regeneratePermissionRows 全部遵守，
 *       与 M10 管理端产品维护（AdminProductServiceImpl）同一口径；新增任何同步表 updateById
 *       都要照此办理；</li>
 *   <li><b>宝宝的 family_id 回填只在创建家庭时做一次</b>（owner 名下 family_id 为空的行）；
 *       M16 做「宝宝进家庭」的显式管理（届时新宝宝如何对成员可见一并收口）。</li>
 * </ul>
 */
@Service
public class FamilyServiceImpl implements FamilyService {

    /** 显示名列宽（bl_family_members.display_name VARCHAR(50)） */
    private static final int DISPLAY_NAME_MAX = 50;

    private final BlFamilyMapper familyMapper;
    private final BlFamilyMemberMapper memberMapper;
    private final BlMemberPermissionMapper permissionMapper;
    private final BlChildMapper childMapper;
    private final IUserFacade userFacade;

    public FamilyServiceImpl(BlFamilyMapper familyMapper, BlFamilyMemberMapper memberMapper,
                             BlMemberPermissionMapper permissionMapper, BlChildMapper childMapper,
                             IUserFacade userFacade) {
        this.familyMapper = familyMapper;
        this.memberMapper = memberMapper;
        this.permissionMapper = permissionMapper;
        this.childMapper = childMapper;
        this.userFacade = userFacade;
    }

    // ==================================================================
    // 家庭
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyVO create(long userId, FamilyCreateCmd cmd) {
        requireNoActiveMembership(userId, "已属于一个家庭，不能重复创建（先让 OWNER 移除或等退出端点上线）");

        BlFamily family = new BlFamily();
        family.setName(cmd.getName().trim());
        family.setOwnerUserId(userId);
        family.setStatus(BlFamily.STATUS_ACTIVE);
        family.setInviteCode(newInviteCode());
        familyMapper.insert(family);

        BlFamilyMember owner = insertMemberRow(family.getId(), userId, BlFamilyMember.ROLE_OWNER);

        // 回填宝宝归属：owner 名下 family_id 为空的宝宝全部归入新家庭（server_updated_at
        // 随 ON UPDATE 前进，owner 的下次拉取会收到这批行；family_id 是服务端独占列，
        // 客户端推宝宝行不会把它覆写回 null——SyncTable.CHILDREN 的 serverOwnedProperties）
        childMapper.update(null, Wrappers.<BlChild>lambdaUpdate()
                .set(BlChild::getFamilyId, family.getId())
                .eq(BlChild::getOwnerUserId, userId)
                .isNull(BlChild::getFamilyId));

        // OWNER 的默认权限行（全读写）：给 App 端权限矩阵页一份可渲染的数据
        insertDefaultPermissionRows(owner, family.getId());
        return toVo(family, owner, activeMemberCount(family.getId()));
    }

    @Override
    public FamilyVO myFamily(long userId) {
        BlFamilyMember me = requireActiveMembership(userId);
        BlFamily family = familyMapper.selectById(me.getFamilyId());
        if (family == null) {
            // 成员行在、家庭行没了：脏数据，按「无家庭」语义回（与 FamilyAccessScopeResolver 一致）
            throw new ServiceException(BabyLogErrorCode.FAMILY_NOT_FOUND);
        }
        return toVo(family, me, activeMemberCount(me.getFamilyId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyVO resetInviteCode(long userId) {
        BlFamilyMember me = requireActiveMembership(userId);
        requireOwner(me);
        BlFamily family = familyMapper.selectById(me.getFamilyId());
        if (family == null) {
            throw new ServiceException(BabyLogErrorCode.FAMILY_NOT_FOUND);
        }
        family.setInviteCode(newInviteCode());
        familyMapper.updateById(family);
        return toVo(family, me, activeMemberCount(me.getFamilyId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyVO join(long userId, FamilyJoinCmd cmd) {
        String code = InviteCodes.normalize(cmd.getInviteCode());
        requireNoActiveMembership(userId, "已属于一个家庭，不能重复加入（先让 OWNER 移除或等退出端点上线）");

        BlFamily family = familyMapper.selectOne(Wrappers.<BlFamily>lambdaQuery()
                .eq(BlFamily::getInviteCode, code)
                .eq(BlFamily::getStatus, BlFamily.STATUS_ACTIVE));
        if (family == null) {
            // 码错/家庭已解散/软删统一一个语义：不给存在性信号
            throw new ServiceException(BabyLogErrorCode.INVITE_CODE_INVALID);
        }

        BlFamilyMember member = insertMemberRow(family.getId(), userId, BlFamilyMember.ROLE_VIEWER);
        // VIEWER 默认权限行：家庭全部宝宝 × 四分类，只读
        insertDefaultPermissionRows(member, family.getId());
        return toVo(family, member, activeMemberCount(family.getId()));
    }

    // ==================================================================
    // 成员
    // ==================================================================

    @Override
    public List<FamilyMemberVO> members(long userId) {
        BlFamilyMember me = requireActiveMembership(userId);
        return memberMapper.selectList(Wrappers.<BlFamilyMember>lambdaQuery()
                        .eq(BlFamilyMember::getFamilyId, me.getFamilyId())
                        .orderByAsc(BlFamilyMember::getJoinedAt))
                .stream()
                .map(FamilyServiceImpl::toMemberVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeRole(long userId, long memberId, MemberRoleCmd cmd) {
        BlFamilyMember me = requireActiveMembership(userId);
        requireOwner(me);
        BlFamilyMember target = requireFamilyMember(me.getFamilyId(), memberId);
        if (target.getId().equals(me.getId())) {
            // OWNER 角色不可转让也不可自降（01B：不可缺失的兜底，避免家庭被锁死）
            throw new ServiceException(BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);
        }
        if (BlFamilyMember.ROLE_OWNER.equals(cmd.getRole())) {
            throw new ServiceException(BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);
        }
        target.setRole(cmd.getRole());
        // updateById：@Version 非空自动 +1（同步可见「角色变了」）。游标列必须置空——
        // 实体从 SELECT 出来 serverUpdatedAt 非空，MP NOT_NULL 策略会把旧值显式 SET 回去，
        // 数据库 ON UPDATE CURRENT_TIMESTAMP(3) 不触发 → 成员行拿不到新 server_updated_at →
        // 增量拉取 0 行（M17 冒烟实测踩坑，与 M10 管理端产品维护同一类坑，修法口径一致）
        target.setServerUpdatedAt(null);
        memberMapper.updateById(target);
        // 权限行按新角色的默认矩阵重建：旧行软删（同步可见「旧权限收回」）+ 新行插入
        regeneratePermissionRows(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kick(long userId, long memberId) {
        BlFamilyMember me = requireActiveMembership(userId);
        requireOwner(me);
        BlFamilyMember target = requireFamilyMember(me.getFamilyId(), memberId);
        if (target.getId().equals(me.getId())) {
            // 踢自己 = 解散家庭/退出，语义不同，M16 的退出/解散端点处理
            throw new ServiceException(BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);
        }
        if (!BlFamilyMember.STATUS_ACTIVE.equals(target.getStatus())) {
            // 重复踢或踢 REMOVED 行：幂等放行（与设备 kick 同口径）
            return;
        }
        target.setStatus(BlFamilyMember.STATUS_REMOVED);
        // 游标置空理由同 changeRole：被踢成员要靠增量拉取看到自己行变 REMOVED（M18 修复）
        target.setServerUpdatedAt(null);
        memberMapper.updateById(target);
        // 权限行不动：成员行 status=REMOVED 已让裁决（FamilyAccessScopeResolver 只认 ACTIVE）
        // 与拉取范围立即失效；行保留是同步事实（客户端要显示「谁被移除了」）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leave(long userId) {
        BlFamilyMember me = requireActiveMembership(userId);
        if (BlFamilyMember.ROLE_OWNER.equals(me.getRole())) {
            // OWNER 退出 = 家庭锁死（无人可管理成员/解散），必须走 dissolve
            throw new ServiceException(BabyLogErrorCode.OWNER_LEAVE_FORBIDDEN);
        }
        me.setStatus(BlFamilyMember.STATUS_REMOVED);
        me.setServerUpdatedAt(null);
        memberMapper.updateById(me);
        // 权限行不动（与踢出同口径）：成员行 REMOVED 已让裁决与拉取范围立即失效
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolve(long userId) {
        BlFamilyMember me = requireActiveMembership(userId);
        requireOwner(me);
        BlFamily family = familyMapper.selectById(me.getFamilyId());
        if (family == null) {
            throw new ServiceException(BabyLogErrorCode.FAMILY_NOT_FOUND);
        }
        family.setStatus(BlFamily.STATUS_DISSOLVED);
        // bl_family 是服务端专属表（BaseDO，无游标列），不存在置空问题；
        // 旧邀请码随 status 失效：join 只查 ACTIVE 家庭，旧码统一 8301（不给存在性信号）
        familyMapper.updateById(family);

        // 全部 ACTIVE 成员行（含 OWNER 自己）置 REMOVED：各成员下一次 myFamily 即 8300 →
        // App 端清身份回落单用户（M17 已实现该分支）；不吊销 token，幂等由「成员行已 REMOVED →
        // requireActiveMembership 抛 8300」天然保证
        List<BlFamilyMember> active = memberMapper.selectList(Wrappers.<BlFamilyMember>lambdaQuery()
                .eq(BlFamilyMember::getFamilyId, me.getFamilyId())
                .eq(BlFamilyMember::getStatus, BlFamilyMember.STATUS_ACTIVE));
        for (BlFamilyMember row : active) {
            row.setStatus(BlFamilyMember.STATUS_REMOVED);
            row.setServerUpdatedAt(null);
            memberMapper.updateById(row);
        }

        // 宝宝归属清空（2026-11-13 龙哥拍板）：与 create 的回填逻辑对称——
        // 解散后重建家庭时，「owner 名下 family_id 为空的宝宝」会重新归入新家庭。
        // wrapper 更新不写游标列，ON UPDATE 自动前进，owner 下次增量拉取收到新行
        // （family_id 是服务端独占列不回传，客户端无感知，version 不变幂等）
        childMapper.update(null, Wrappers.<BlChild>lambdaUpdate()
                .set(BlChild::getFamilyId, null)
                .eq(BlChild::getFamilyId, me.getFamilyId()));
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void requireNoActiveMembership(long userId, String message) {
        Long count = memberMapper.selectCount(Wrappers.<BlFamilyMember>lambdaQuery()
                .eq(BlFamilyMember::getUserId, userId)
                .eq(BlFamilyMember::getStatus, BlFamilyMember.STATUS_ACTIVE));
        if (count != null && count > 0) {
            throw new ServiceException(BabyLogErrorCode.MEMBER_ALREADY_JOINED, message);
        }
    }

    private BlFamilyMember requireActiveMembership(long userId) {
        List<BlFamilyMember> mine = memberMapper.selectList(Wrappers.<BlFamilyMember>lambdaQuery()
                .eq(BlFamilyMember::getUserId, userId)
                .eq(BlFamilyMember::getStatus, BlFamilyMember.STATUS_ACTIVE)
                .orderByDesc(BlFamilyMember::getJoinedAt));
        // create/join 双守卫保证至多一条；多条（脏数据）取最新加入，与 FamilyAccessScopeResolver 同口径
        if (mine.isEmpty()) {
            throw new ServiceException(BabyLogErrorCode.FAMILY_NOT_FOUND);
        }
        return mine.get(0);
    }

    private static void requireOwner(BlFamilyMember me) {
        if (!BlFamilyMember.ROLE_OWNER.equals(me.getRole())) {
            throw new ServiceException(BabyLogErrorCode.OWNER_TRANSFER_FORBIDDEN);
        }
    }

    private BlFamilyMember requireFamilyMember(Long familyId, long memberId) {
        BlFamilyMember target = memberMapper.selectById(memberId);
        // 猜别人的成员行不给存在性信号，统一 8302（与设备 kick 的 8360 同思路）
        if (target == null || !familyId.equals(target.getFamilyId())) {
            throw new ServiceException(BabyLogErrorCode.NOT_FAMILY_MEMBER);
        }
        return target;
    }

    private String newInviteCode() {
        Predicate<String> exists = code -> {
            Long count = familyMapper.selectCount(Wrappers.<BlFamily>lambdaQuery()
                    .eq(BlFamily::getInviteCode, code));
            return count != null && count > 0;
        };
        return InviteCodes.generate(exists);
    }

    private BlFamilyMember insertMemberRow(Long familyId, long userId, String role) {
        BlFamilyMember row = new BlFamilyMember();
        row.setFamilyId(familyId);
        row.setUserId(userId);
        row.setUserType("APP");
        row.setDisplayName(resolveDisplayName(userId));
        row.setRole(role);
        row.setStatus(BlFamilyMember.STATUS_ACTIVE);
        row.setJoinedAt(System.currentTimeMillis());
        memberMapper.insert(row);
        return row;
    }

    private String resolveDisplayName(long userId) {
        String fallback = "user-" + userId;
        UserDTO user = userFacade.getById(userId);
        if (user == null) {
            return truncate(fallback);
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return truncate(user.getNickname().trim());
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return truncate(user.getUsername().trim());
        }
        return truncate(fallback);
    }

    private static String truncate(String raw) {
        return raw.length() <= DISPLAY_NAME_MAX ? raw : raw.substring(0, DISPLAY_NAME_MAX);
    }

    private int activeMemberCount(Long familyId) {
        Long count = memberMapper.selectCount(Wrappers.<BlFamilyMember>lambdaQuery()
                .eq(BlFamilyMember::getFamilyId, familyId)
                .eq(BlFamilyMember::getStatus, BlFamilyMember.STATUS_ACTIVE));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 按角色默认矩阵生成权限行（家庭全部宝宝 × 四分类）。
     * 归属 user_id = 该成员（其 App 端拉自己的权限矩阵）；server_updated_at 自动前进 → 同步可见。
     * <b>只插缺失的行</b>（宝宝 × 分类已有行时跳过，防 uk 撞键——调用方 join/create 全新成员、
     * regenerate 可能带着已有行进来）。
     */
    private void insertDefaultPermissionRows(BlFamilyMember member, Long familyId) {
        List<BlChild> children = childMapper.selectList(Wrappers.<BlChild>lambdaQuery()
                .eq(BlChild::getFamilyId, familyId));
        Set<SyncCategory> writable = FamilyPermissions.defaultWritable(member.getRole());
        List<BlMemberPermission> existing = permissionMapper.selectList(Wrappers.<BlMemberPermission>lambdaQuery()
                .eq(BlMemberPermission::getMemberId, member.getId()));
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (BlMemberPermission row : existing) {
            taken.add(row.getChildId() + ":" + row.getCategory());
        }
        long now = System.currentTimeMillis();
        for (BlChild child : children) {
            for (SyncCategory category : SyncCategory.values()) {
                if (!taken.add(child.getId() + ":" + category.name())) {
                    continue; // 该（宝宝×分类）已有行：改值走 regenerate 的原地更新，不重插
                }
                BlMemberPermission row = new BlMemberPermission();
                row.setMemberId(member.getId());
                row.setChildId(child.getId());
                row.setCategory(category.name());
                row.setCanRead(1);
                row.setCanWrite(writable.contains(category) ? 1 : 0);
                row.setUserId(member.getUserId());
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                permissionMapper.insert(row);
            }
        }
    }

    /**
     * 改角色后对齐权限行：<b>原地更新</b>已有行的 can_write（行 id 不变、version+1、
     * server_updated_at 前进，同步语义是「权限值变了」）；缺行（新宝宝）补插。
     *
     * <p>为什么不能软删重建：uk(member_id, child_id, category) 不含 deleted 列，
     * 软删行仍占着键位，重插必撞 DuplicateKey（dev 冒烟实测，M15）。
     * 原地更新附带的好处：App 端本地行直接被覆盖，无需处理「删旧插新」的两步。
     */
    private void regeneratePermissionRows(BlFamilyMember member) {
        Set<SyncCategory> writable = FamilyPermissions.defaultWritable(member.getRole());
        List<BlMemberPermission> existing = permissionMapper.selectList(Wrappers.<BlMemberPermission>lambdaQuery()
                .eq(BlMemberPermission::getMemberId, member.getId()));
        long now = System.currentTimeMillis();
        for (BlMemberPermission row : existing) {
            SyncCategory category = parseCategory(row.getCategory());
            int target = category != null && writable.contains(category) ? 1 : 0;
            if (!Integer.valueOf(target).equals(row.getCanWrite())) {
                row.setCanRead(1);
                row.setCanWrite(target);
                row.setUpdatedAt(now);
                // updateById：@Version 非空自动 +1，同步可见。游标置空理由同 changeRole——
                // 权限行必须拿到新 server_updated_at，否则改角色后成员增量拉取 0 行（M18 修复）
                row.setServerUpdatedAt(null);
                permissionMapper.updateById(row);
            }
        }
        insertDefaultPermissionRows(member, member.getFamilyId());
    }

    private static SyncCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SyncCategory.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static FamilyVO toVo(BlFamily family, BlFamilyMember me, int memberCount) {
        FamilyVO vo = new FamilyVO();
        vo.setId(family.getId());
        vo.setName(family.getName());
        // 邀请码只给 OWNER：非创建者不需要，也不该拿着码继续拉人
        vo.setInviteCode(BlFamilyMember.ROLE_OWNER.equals(me.getRole()) ? family.getInviteCode() : null);
        vo.setMemberId(me.getId());
        vo.setRole(me.getRole());
        vo.setStatus(family.getStatus());
        vo.setMemberCount(memberCount);
        return vo;
    }

    private static FamilyMemberVO toMemberVo(BlFamilyMember row) {
        FamilyMemberVO vo = new FamilyMemberVO();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setDisplayName(row.getDisplayName());
        vo.setRole(row.getRole());
        vo.setStatus(row.getStatus());
        vo.setUserType(row.getUserType());
        vo.setJoinedAt(row.getJoinedAt());
        return vo;
    }
}
