package com.babylog.biz.device.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.device.service.DeviceService;
import com.babylog.biz.device.vo.DeviceVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App 端设备管理端点。
 *
 * <p>与 M5/M6 的 App 端点同口径：类上 {@code @SaCheckLogin(app-user)}、
 * token 走 {@code Authorization: <裸值>}、user_id 一律从 token 取（请求传什么都不算数）。
 */
@Tag(name = "App 设备管理（BabyLog）", description = "列出设备 / 踢出设备")
@RestController
@RequestMapping("/app/babylog/device")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogDeviceController {

    private final DeviceService deviceService;

    public BabyLogDeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Operation(summary = "列出本账号全部设备（含已移除，按最后活跃倒序）")
    @GetMapping("/list")
    public R<List<DeviceVO>> list() {
        return R.ok(deviceService.list(currentUserId()));
    }

    @Operation(summary = "踢出设备（拦 refresh，重新登录可复活；workerId 保留不回收）")
    @PostMapping("/{id}/kick")
    public R<Void> kick(@PathVariable("id") Long id) {
        deviceService.kick(currentUserId(), id);
        return R.ok();
    }

    /** 与 BabyLogSyncController 同口径：会话数据被清时按 1002 回，让客户端走 refresh */
    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
