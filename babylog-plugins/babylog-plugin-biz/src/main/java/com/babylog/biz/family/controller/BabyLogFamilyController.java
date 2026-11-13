package com.babylog.biz.family.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.family.dto.FamilyCreateCmd;
import com.babylog.biz.family.dto.FamilyJoinCmd;
import com.babylog.biz.family.dto.MemberRoleCmd;
import com.babylog.biz.family.service.FamilyService;
import com.babylog.biz.family.vo.FamilyMemberVO;
import com.babylog.biz.family.vo.FamilyVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App 端家庭管理端点（V2.1 M15）。
 *
 * <p>与 M5/M6/M8 的 App 端点同口径：类上 {@code @SaCheckLogin(app-user)}、
 * token 走 {@code Authorization: <裸值>}、userId 一律从 token 取（请求传什么都不算数）。
 * 无家庭用户（多数存量用户）走创建/加入前不碰其它端点。
 */
@Tag(name = "App 家庭管理（BabyLog）", description = "创建家庭 / 邀请码 / 成员管理")
@RestController
@RequestMapping("/app/babylog/family")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogFamilyController {

    private final FamilyService familyService;

    public BabyLogFamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @Operation(summary = "创建家庭（创建者默认 OWNER，宝宝归属自动回填）")
    @PostMapping
    public R<FamilyVO> create(@Validated @RequestBody FamilyCreateCmd cmd) {
        return R.ok(familyService.create(currentUserId(), cmd));
    }

    @Operation(summary = "我的家庭（无家庭返回 8300；邀请码仅 OWNER 可见）")
    @GetMapping
    public R<FamilyVO> myFamily() {
        return R.ok(familyService.myFamily(currentUserId()));
    }

    @Operation(summary = "重置邀请码（仅 OWNER；旧码立即失效）")
    @PostMapping("/invite/reset")
    public R<FamilyVO> resetInviteCode() {
        return R.ok(familyService.resetInviteCode(currentUserId()));
    }

    @Operation(summary = "凭邀请码加入家庭（新成员默认 VIEWER 只读）")
    @PostMapping("/join")
    public R<FamilyVO> join(@Validated @RequestBody FamilyJoinCmd cmd) {
        return R.ok(familyService.join(currentUserId(), cmd));
    }

    @Operation(summary = "成员列表（含已被移除的成员，按加入时间正序）")
    @GetMapping("/members")
    public R<List<FamilyMemberVO>> members() {
        return R.ok(familyService.members(currentUserId()));
    }

    @Operation(summary = "修改成员角色（仅 OWNER；OWNER 不可指定不可转让）")
    @PutMapping("/members/{id}/role")
    public R<Void> changeRole(@PathVariable("id") Long id, @Validated @RequestBody MemberRoleCmd cmd) {
        familyService.changeRole(currentUserId(), id, cmd);
        return R.ok();
    }

    @Operation(summary = "踢出成员（仅 OWNER；行保留仅置 REMOVED，被踢者立即回落单用户范围，可凭新邀请码再加入）")
    @PostMapping("/members/{id}/kick")
    public R<Void> kick(@PathVariable("id") Long id) {
        familyService.kick(currentUserId(), id);
        return R.ok();
    }

    @Operation(summary = "成员自助退出（OWNER 不可退出须先解散 → 8305；退出后立即回落单用户范围）")
    @PostMapping("/leave")
    public R<Void> leave() {
        familyService.leave(currentUserId());
        return R.ok();
    }

    @Operation(summary = "解散家庭（仅 OWNER → 8304；家庭置 DISSOLVED，成员全部 REMOVED，宝宝归属清空待重建家庭回填）")
    @PostMapping("/dissolve")
    public R<Void> dissolve() {
        familyService.dissolve(currentUserId());
        return R.ok();
    }

    /** 与 BabyLogSyncController / BabyLogDeviceController 同口径：会话被清时按 1002 回，让客户端走 refresh */
    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
