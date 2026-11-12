package com.babylog.biz.sync.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.sync.dto.SyncPushCmd;
import com.babylog.biz.sync.service.SyncService;
import com.babylog.biz.sync.vo.SyncChangesVO;
import com.babylog.biz.sync.vo.SyncPushResultVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 端增量同步端点。
 *
 * <p>鉴权在<b>类上</b>声明 {@code @SaCheckLogin(app-user)}：这两个端点没有一条是匿名可用的，
 * 逐个方法加注解只会漏（A11 规则的存在理由）。token 走 {@code Authorization: <裸值>}，
 * 与 M5 认证端点同口径。
 *
 * <p>两个端点都是「账号私有」语义：拉取只返回 {@code user_id = 自己} 的行，
 * 推送只写自己名下（user_id 由 token 盖章，请求体里传什么都不算数）。
 */
@Tag(name = "App 同步（BabyLog）", description = "增量拉取 changes / 批量推送 push")
@RestController
@RequestMapping("/app/babylog/sync")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogSyncController {

    private final SyncService syncService;

    public BabyLogSyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @Operation(summary = "增量拉取（按服务端游标 since + sinceId）")
    @GetMapping("/changes")
    public R<SyncChangesVO> changes(@RequestParam(required = false) Long since,
                                    @RequestParam(required = false) Long sinceId,
                                    @RequestParam(required = false) String categories,
                                    @RequestParam(required = false) Integer limit) {
        return R.ok(syncService.pull(currentUserId(), since, sinceId, categories, limit));
    }

    @Operation(summary = "批量推送（单次上限 200 条，逐条 version 乐观锁 upsert）")
    @PostMapping("/push")
    public R<SyncPushResultVO> push(@Validated @RequestBody SyncPushCmd cmd) {
        return R.ok(syncService.push(currentUserId(), cmd));
    }

    /**
     * 取当前 app-user 账号。
     *
     * <p>正常路径上 {@code @SaCheckLogin} 会先抛出未登录异常，这里为 null 只可能是
     * 会话数据被清（Redis 掉了但 token 还没过期）——按 1002 回，让客户端走 refresh。
     */
    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
