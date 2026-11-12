package com.babylog.biz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BabyLog App 端认证配置（前缀 {@code babylog.auth}，与 {@code babylog.auth.guard} 同级不同子键）。
 *
 * <p>口径（2026-11-12 会话 M5 拍板）：**双 token**——access 短（2h，Sa-Token 持有），
 * refresh 长（30d，Redis 持有哈希），access 过期由客户端拿 refresh 换新，refresh 过期才回登录页。
 *
 * <p>全部键值内联 profile yml（不用 .env / 环境变量），与三环境配置口径一致。
 */
@ConfigurationProperties(prefix = "babylog.auth")
public class BabyLogAuthProperties {

    /** access token 有效期（秒），默认 2 小时 */
    private long accessTokenTtlSeconds = 7200L;

    /** refresh token 有效期（秒），默认 30 天 */
    private long refreshTokenTtlSeconds = 30L * 24 * 3600;

    /** 邮箱验证码相关 */
    private final Email email = new Email();

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public Email getEmail() {
        return email;
    }

    /**
     * 邮箱验证码策略：短有效期 + 冷却 + 日配额 + 错误次数作废，
     * 四个维度独立生效，任一项超限都只回 837x 业务码，不泄露「邮箱是否存在」。
     */
    public static class Email {

        /** 验证码有效期（秒） */
        private long codeTtlSeconds = 300L;

        /** 同邮箱两次发送的最小间隔（秒） */
        private long resendIntervalSeconds = 60L;

        /** 同邮箱每日发送上限（次） */
        private int dailyLimit = 10;

        /** 单个验证码允许输错的次数，超限即作废 */
        private int maxVerifyAttempts = 5;

        /** 邮件主题 */
        private String subject = "BabyLog 登录验证码";

        /** 发件人地址；留空则回退 spring.mail.username */
        private String from = "";

        public long getCodeTtlSeconds() {
            return codeTtlSeconds;
        }

        public void setCodeTtlSeconds(long codeTtlSeconds) {
            this.codeTtlSeconds = codeTtlSeconds;
        }

        public long getResendIntervalSeconds() {
            return resendIntervalSeconds;
        }

        public void setResendIntervalSeconds(long resendIntervalSeconds) {
            this.resendIntervalSeconds = resendIntervalSeconds;
        }

        public int getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(int dailyLimit) {
            this.dailyLimit = dailyLimit;
        }

        public int getMaxVerifyAttempts() {
            return maxVerifyAttempts;
        }

        public void setMaxVerifyAttempts(int maxVerifyAttempts) {
            this.maxVerifyAttempts = maxVerifyAttempts;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }
}
