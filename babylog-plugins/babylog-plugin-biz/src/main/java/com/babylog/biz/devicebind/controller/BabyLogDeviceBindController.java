package com.babylog.biz.devicebind.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.devicebind.dto.DeviceBindApplyCmd;
import com.babylog.biz.devicebind.dto.DeviceBindPollCmd;
import com.babylog.biz.devicebind.dto.DeviceBindUserCodeCmd;
import com.babylog.biz.devicebind.service.DeviceBindService;
import com.babylog.biz.devicebind.vo.DeviceBindApplyVO;
import com.babylog.biz.devicebind.vo.DeviceBindPollVO;
import com.babylog.biz.devicebind.vo.DeviceBindStatusVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 电视设备码绑定；userId 只从手机 app-user 登录上下文取。 */
@Tag(name = "电视设备码绑定（BabyLog）", description = "电视申请与轮询、手机确认与拒绝")
@RestController
@RequestMapping("/app/babylog/device-bind")
public class BabyLogDeviceBindController {

    private final DeviceBindService deviceBindService;

    public BabyLogDeviceBindController(DeviceBindService deviceBindService) {
        this.deviceBindService = deviceBindService;
    }

    @Operation(summary = "电视匿名申请设备码")
    @PostMapping("/apply")
    public R<DeviceBindApplyVO> apply(@Validated @RequestBody DeviceBindApplyCmd cmd,
                                      HttpServletRequest request) {
        return R.ok(deviceBindService.apply(cmd, clientIp(request)));
    }

    @Operation(summary = "电视匿名轮询；approved 仅一次性返回双 token")
    @PostMapping("/poll")
    public R<DeviceBindPollVO> poll(@Validated @RequestBody DeviceBindPollCmd cmd) {
        return R.ok(deviceBindService.poll(cmd));
    }

    @Operation(summary = "手机确认 6 位码；绑定当前登录账号")
    @PostMapping("/confirm")
    @SaCheckLogin(type = StpAppUtil.TYPE)
    public R<DeviceBindStatusVO> confirm(@Validated @RequestBody DeviceBindUserCodeCmd cmd,
                                         HttpServletRequest request) {
        return R.ok(deviceBindService.confirm(currentUserId(), cmd, clientIp(request)));
    }

    @Operation(summary = "手机拒绝待处理申请")
    @PostMapping("/deny")
    @SaCheckLogin(type = StpAppUtil.TYPE)
    public R<DeviceBindStatusVO> deny(@Validated @RequestBody DeviceBindUserCodeCmd cmd,
                                      HttpServletRequest request) {
        return R.ok(deviceBindService.deny(currentUserId(), cmd, clientIp(request)));
    }

    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 与既有 App token 登录审计同口径：XFF 首段优先，回落 remoteAddr。 */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
