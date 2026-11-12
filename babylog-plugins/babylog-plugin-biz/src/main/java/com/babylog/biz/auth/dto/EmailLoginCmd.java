package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 邮箱验证码登录请求（邮箱未注册时自动建档） */
@Data
public class EmailLoginCmd {

    @Schema(description = "邮箱", example = "someone@example.com")
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @Schema(description = "6 位验证码")
    @NotBlank(message = "验证码不能为空")
    private String code;

    @Schema(description = "设备信息（可选）")
    private DeviceInfo device;
}
