package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 刷新 access token 请求 */
@Data
public class RefreshTokenCmd {

    @Schema(description = "登录时下发的 refresh token")
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
