package com.babylog.biz.family.service;

import com.babylog.biz.family.dto.FamilyCreateCmd;
import com.babylog.biz.family.dto.FamilyJoinCmd;
import com.babylog.biz.family.dto.MemberRoleCmd;
import com.babylog.biz.family.vo.FamilyMemberVO;
import com.babylog.biz.family.vo.FamilyVO;

import java.util.List;

/**
 * 家庭与成员管理（V2.1 M15，`/app/babylog/family/**`）。
 *
 * <p>口径：一个账号同一时间至多一条 ACTIVE 成员行（创建/加入双守卫）；
 * 踢出 = 成员行 status 置 REMOVED（行保留、同步可见），权限裁决只认 ACTIVE。
 */
public interface FamilyService {

    /** 创建家庭：落 bl_family + OWNER 成员行 + 回填自己宝宝的 family_id + 默认权限行。已在家 → 8303 */
    FamilyVO create(long userId, FamilyCreateCmd cmd);

    /** 我的家庭（无家庭 → 8300；inviteCode 仅 OWNER 可见） */
    FamilyVO myFamily(long userId);

    /** 重置邀请码（仅 OWNER，非 OWNER → 8304） */
    FamilyVO resetInviteCode(long userId);

    /** 凭邀请码加入：落 VIEWER 成员行 + 默认权限行（家庭宝宝 × 四分类只读）。码无效/家庭不存在 → 8301 */
    FamilyVO join(long userId, FamilyJoinCmd cmd);

    /** 成员列表（按加入时间正序，含 REMOVED——用户需要知道谁被移除了；无家庭 → 8300） */
    List<FamilyMemberVO> members(long userId);

    /** 改成员角色（仅 OWNER → 8304；不可改自己/不可指定 OWNER；目标须为本家庭成员 → 8302） */
    void changeRole(long userId, long memberId, MemberRoleCmd cmd);

    /** 踢出成员（仅 OWNER → 8304；不可踢自己；目标须为本家庭 ACTIVE 成员 → 8302）。行保留，status=REMOVED */
    void kick(long userId, long memberId);
}
