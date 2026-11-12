package com.babylog.biz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 退出登录请求：带上 refresh token 才能把它一并作废（不传则只注销当前 access token） */
@Data
public class LogoutCmd {

    @Schema(description = "登录时下发的 refresh token（可选）")
    private String refreshToken;
}
