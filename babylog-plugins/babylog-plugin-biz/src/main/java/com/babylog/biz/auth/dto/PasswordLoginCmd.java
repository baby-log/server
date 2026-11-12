package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 账密登录请求（与 /app/auth/login 同源，差别是签发双 token） */
@Data
public class PasswordLoginCmd {

    @Schema(description = "账号：用户名或邮箱", example = "someone@example.com")
    @NotBlank(message = "账号不能为空")
    private String account;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "设备信息（可选）")
    private DeviceInfo device;
}
