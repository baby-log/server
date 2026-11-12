package com.babylog.biz.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录/刷新结果：双 token 形态。
 *
 * <p>access token 走 Sa-Token（app-user 体系，header {@code Authorization: <token>}），
 * refresh token 是服务端随机串，只在 Redis 存哈希——客户端拿到明文，
 * 服务端泄露也换不出可用凭证。
 */
@Data
public class LoginTokenVO {

    @Schema(description = "access token（放 Authorization 头，裸值不带 Bearer 前缀）")
    private String accessToken;

    @Schema(description = "access token 有效期（秒）")
    private long accessExpiresIn;

    @Schema(description = "refresh token（access 过期后用来换新 access）")
    private String refreshToken;

    @Schema(description = "refresh token 剩余有效期（秒）")
    private long refreshExpiresIn;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "本次是否新建账号（邮箱验证码首登为 true）")
    private boolean newUser;

    @Schema(description = "设备行 ID（未上报设备标识时为 null）")
    private Long deviceId;
}
