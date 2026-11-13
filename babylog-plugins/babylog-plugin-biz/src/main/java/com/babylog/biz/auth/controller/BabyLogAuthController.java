package com.babylog.biz.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.auth.dto.EmailCodeCmd;
import com.babylog.biz.auth.dto.EmailLoginCmd;
import com.babylog.biz.auth.dto.LogoutCmd;
import com.babylog.biz.auth.dto.MiniLoginCmd;
import com.babylog.biz.auth.dto.PasswordLoginCmd;
import com.babylog.biz.auth.dto.RefreshTokenCmd;
import com.babylog.biz.auth.dto.RegisterCmd;
import com.babylog.biz.auth.service.AppAuthService;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.biz.auth.vo.LoginTokenVO;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BabyLog App 认证补充端点。
 *
 * <p>为什么不直接扩 system 的 {@code /app/auth/*}：那是脚手架端点，改它等于把 BabyLog 的
 * 业务差异（邮箱验证码、双 token、设备接线）塞进上游代码，后续框架升级每次都要冲突。
 * 这里只做「补一条登录路」，账号体系仍是 {@code sys_user}，Token 仍是 app-user 体系。
 *
 * <p>签名与 {@code /app/auth/login} 对齐：{@code R<T>} + 业务错误码（8300 段），
 * token 放 {@code Authorization} 头裸值，与全项目口径一致。
 */
@Tag(name = "App 认证（BabyLog）", description = "邮箱验证码 / 账密注册 / 双 token 刷新")
@RestController
@RequestMapping("/app/babylog/auth")
public class BabyLogAuthController {

    private final AppAuthService authService;

    public BabyLogAuthController(AppAuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "发送登录验证码（匿名）")
    @PostMapping("/email/code")
    public R<CodeSendVO> sendLoginCode(@Validated @RequestBody EmailCodeCmd cmd) {
        return R.ok(authService.sendLoginCode(cmd));
    }

    @Operation(summary = "邮箱验证码登录（匿名，邮箱未注册则自动建档）")
    @PostMapping("/email/login")
    public R<LoginTokenVO> loginByEmailCode(@Validated @RequestBody EmailLoginCmd cmd) {
        return R.ok(authService.loginByEmailCode(cmd));
    }

    @Operation(summary = "账密注册（匿名，注册即登录）")
    @PostMapping("/register")
    public R<LoginTokenVO> register(@Validated @RequestBody RegisterCmd cmd) {
        return R.ok(authService.register(cmd));
    }

    @Operation(summary = "账密登录（匿名，account 可为用户名或邮箱）")
    @PostMapping("/password/login")
    public R<LoginTokenVO> loginByPassword(@Validated @RequestBody PasswordLoginCmd cmd) {
        return R.ok(authService.loginByPassword(cmd));
    }

    @Operation(summary = "微信小程序登录（匿名，wx.login code 换 app-user 双 token，未绑定自动建档）")
    @PostMapping("/mini/login")
    public R<LoginTokenVO> loginByWxMini(@Validated @RequestBody MiniLoginCmd cmd) {
        return R.ok(authService.loginByWxMini(cmd));
    }

    @Operation(summary = "刷新 access token（匿名，凭 refresh token）")
    @PostMapping("/refresh")
    public R<LoginTokenVO> refresh(@Validated @RequestBody RefreshTokenCmd cmd) {
        return R.ok(authService.refresh(cmd));
    }

    @Operation(summary = "退出登录（需 app-user 登录态）")
    @PostMapping("/logout")
    @SaCheckLogin(type = StpAppUtil.TYPE)
    public R<Void> logout(@RequestBody(required = false) LogoutCmd cmd) {
        authService.logout(cmd == null ? null : cmd.getRefreshToken());
        return R.ok();
    }
}
