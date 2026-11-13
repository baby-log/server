package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信小程序登录请求（wx.login 的 code 换 app-user 双 token）。
 *
 * <p>小程序端不需要注册/验证码/账密——微信身份即账号：未绑定的 openId 首登自动建档。
 */
@Data
public class MiniLoginCmd {

    @Schema(description = "wx.login() 下发的 code（一次性，5 分钟有效）", example = "0a3Xk9000Qw9rT1Vz0000f3Xk9000Qw")
    @NotBlank(message = "微信登录 code 不能为空")
    private String code;

    @Schema(description = "设备信息（可选；小程序端建议传，用于设备管理与雪花 workerId 分配）")
    private DeviceInfo device;
}
