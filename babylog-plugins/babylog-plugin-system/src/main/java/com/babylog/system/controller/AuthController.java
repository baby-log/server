package com.babylog.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import com.babylog.system.domain.dto.LoginBody;
import com.babylog.system.domain.vo.LoginVO;
import com.babylog.system.domain.vo.UserInfoVO;
import com.babylog.system.service.SysLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口（登录 / 登出 / 当前用户信息） */
@Tag(name = "认证管理", description = "登录、登出、当前用户信息")
@RestController
@RequestMapping("/system/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysLoginService loginService;

    /** 账号密码登录 */
    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Validated @RequestBody LoginBody body) {
        return R.ok(loginService.login(body));
    }

    /** 退出登录 */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    @SaCheckLogin(type = StpSysUtil.TYPE)
    public R<Void> logout() {
        loginService.logout();
        return R.ok();
    }

    /** 当前登录用户信息 */
    @Operation(summary = "当前登录用户信息")
    @GetMapping("/getInfo")
    @SaCheckLogin(type = StpSysUtil.TYPE)
    public R<UserInfoVO> getInfo() {
        return R.ok(loginService.getInfo());
    }
}
