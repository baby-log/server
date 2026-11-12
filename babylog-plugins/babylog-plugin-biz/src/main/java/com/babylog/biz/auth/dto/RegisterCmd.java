package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 账密注册请求（邮箱验证码登录之外的兜底注册路径） */
@Data
public class RegisterCmd {

    @Schema(description = "用户名（3-32 位，字母/数字/下划线）")
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名须为 3-32 位字母、数字或下划线")
    private String username;

    @Schema(description = "密码（8-64 位）")
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须为 8-64 位")
    private String password;

    @Schema(description = "邮箱（可选，绑定后可用邮箱验证码登录同一账号）")
    private String email;

    @Schema(description = "昵称（可选，缺省取用户名）")
    @Size(max = 32, message = "昵称最长 32 字")
    private String nickname;

    @Schema(description = "设备信息（可选）")
    private DeviceInfo device;
}
