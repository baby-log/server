package com.babylog.biz.auth.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * refresh token 的 Redis 实现。
 *
 * <p>值编码为 {@code userId|deviceUuid}，键为 {@code bl:auth:rt:<sha256(token)>}。
 * 之所以不轮换 refresh token：V2.0 只有一台设备一个人用，轮换带来的「并发刷新互斥」
 * 复杂度会落到还没改造的 App 端；等 V2.2 多设备/多家庭成员时再上轮换 + 复用检测。
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "bl:auth:rt:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String issue(Long userId, String deviceUuid, Duration ttl) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String value = userId + "|" + (deviceUuid == null ? "" : deviceUuid);
        redis.opsForValue().set(KEY_PREFIX + sha256(token), value, ttl);
        return token;
    }

    @Override
    public Optional<RefreshTokenRecord> resolve(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        String key = KEY_PREFIX + sha256(tokenValue);
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        int split = value.indexOf('|');
        if (split <= 0) {
            // 值被写坏（非常规路径）：当作无效凭证，顺手清掉
            redis.delete(key);
            return Optional.empty();
        }
        Long userId;
        try {
            userId = Long.valueOf(value.substring(0, split));
        } catch (NumberFormatException e) {
            redis.delete(key);
            return Optional.empty();
        }
        String deviceUuid = value.substring(split + 1);
        Long expireMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        long remaining = expireMillis == null || expireMillis < 0 ? 0L : expireMillis / 1000L;
        return Optional.of(new RefreshTokenRecord(userId, deviceUuid, remaining));
    }

    @Override
    public void revoke(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return;
        }
        redis.delete(KEY_PREFIX + sha256(tokenValue));
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JLS 强制支持的算法，正常不可能走到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
