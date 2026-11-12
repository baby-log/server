package com.babylog.biz.device.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.babylog.biz.device.service.AdminDeviceService;
import com.babylog.biz.device.vo.AdminDeviceOverviewVO;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端设备查看（sys-admin 体系，M10）。权限字符对应菜单 SQL V2.2.9 的 5020 树。
 */
@Tag(name = "BabyLog 设备查看（管理端）", description = "按账号查设备 / 踢出设备")
@RestController
@RequestMapping("/babylog/device")
public class AdminDeviceController {

    private final AdminDeviceService adminDeviceService;

    public AdminDeviceController(AdminDeviceService adminDeviceService) {
        this.adminDeviceService = adminDeviceService;
    }

    @Operation(summary = "按账号查设备总览（账号 = 用户名或邮箱）")
    @GetMapping("/overview")
    @SaCheckPermission(value = "babylog:device:list", type = StpSysUtil.TYPE)
    public R<AdminDeviceOverviewVO> overview(
            @Parameter(description = "用户名或邮箱", required = true)
            @RequestParam("account") String account) {
        return R.ok(adminDeviceService.overview(account));
    }

    @Operation(summary = "踢出设备（与本人移除同语义：拦 refresh，重新登录可复活）")
    @PostMapping("/{id}/kick")
    @SaCheckPermission(value = "babylog:device:kick", type = StpSysUtil.TYPE)
    public R<Void> kick(@PathVariable("id") Long id) {
        adminDeviceService.kick(id);
        return R.ok();
    }
}
