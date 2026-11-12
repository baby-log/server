package com.babylog.biz.auth.service.impl;

import cn.dev33.satoken.session.SaSession;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.biz.auth.service.AppTokenService;
import com.babylog.common.api.context.LoginUser;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.auth.support.AuthSessionHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * access token 签发实现：走 app-user 独立 StpLogic，按次登录覆盖有效期。
 *
 * <p>关键点（M5 实测确认，sa-token 1.45.0）：{@code StpLogic.login(loginId, timeoutSeconds)}
 * 支持**单次登录覆盖全局 timeout**，所以不需要给 app-user 单独定义一个 StpLogic 子类，
 * 也不需要动 starter-auth——全局 24h 对 /sys 体系保持不变，App 侧拿到 2h。
 */
@Service
public class SaTokenAppTokenService implements AppTokenService {

    private final BabyLogAuthProperties properties;
    private final HttpServletRequest request;

    public SaTokenAppTokenService(BabyLogAuthProperties properties, HttpServletRequest request) {
        this.properties = properties;
        this.request = request;
    }

    @Override
    public String issueAccessToken(Long userId, String username) {
        long ttl = properties.getAccessTokenTtlSeconds();
        StpAppUtil.STP.login(userId, ttl);
        String tokenValue = StpAppUtil.STP.getTokenValue();
        SaSession tokenSession = StpAppUtil.STP.getTokenSessionByToken(tokenValue);
        AuthSessionHolder.saveLoginUser(tokenSession,
                new LoginUser(userId, username, StpAppUtil.TYPE, null));
        // 与 /app/auth/login 同款登录元信息：在线用户列表与审计都依赖这两个键
        long now = System.currentTimeMillis();
        tokenSession.set("LOGIN_IP", clientIp());
        tokenSession.set("LOGIN_TIME", now);
        tokenSession.set("LAST_ACTIVE_TIME", now);
        return tokenValue;
    }

    @Override
    public void logoutCurrent() {
        StpAppUtil.logout();
    }

    @Override
    public long accessTtlSeconds() {
        return properties.getAccessTokenTtlSeconds();
    }

    private String clientIp() {
        String[] headers = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
