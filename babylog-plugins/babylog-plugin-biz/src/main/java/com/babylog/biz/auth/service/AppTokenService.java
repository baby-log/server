package com.babylog.biz.auth.service;

/**
 * access token 签发（app-user 账号体系）。
 *
 * <p>抽成接口是为了让认证编排逻辑可单测——Sa-Token 依赖请求上下文，不该进单测。
 */
public interface AppTokenService {

    /**
     * 签发 access token，并把登录态写进 Sa-Token 会话
     * （LoginUser / LOGIN_IP / LOGIN_TIME，口径与 /app/auth/login 一致，供在线用户列表使用）。
     *
     * @return token 明文（客户端放 Authorization 头，裸值）
     */
    String issueAccessToken(Long userId, String username);

    /** 注销当前请求携带的 access token */
    void logoutCurrent();

    /** access token 有效期（秒），随 token 一起返回给客户端 */
    long accessTtlSeconds();
}
