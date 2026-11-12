package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发送邮箱验证码请求 */
@Data
public class EmailCodeCmd {

    @Schema(description = "接收验证码的邮箱", example = "someone@example.com")
    @NotBlank(message = "邮箱不能为空")
    private String email;
}
