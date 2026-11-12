package com.babylog.biz.auth.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 验证码存储的 Redis 实现。
 *
 * <p>键设计（全部 {@code bl:auth:code:} 前缀，便于运维按前缀巡检与清理）：
 * <ul>
 *   <li>{@code bl:auth:code:{email}} 验证码本体，TTL = codeTtlSeconds</li>
 *   <li>{@code bl:auth:code:cooldown:{email}} 冷却占位，SETNX + TTL，天然并发安全</li>
 *   <li>{@code bl:auth:code:daily:{email}:{yyyy-MM-dd}} 当日发送计数</li>
 *   <li>{@code bl:auth:code:fail:{email}} 当前验证码的错误计数</li>
 * </ul>
 *
 * <p>邮箱进键前已由服务层归一化（trim + 小写），避免大小写差异绕过限流。
 */
@Component
public class RedisEmailCodeStore implements EmailCodeStore {

    private static final String CODE_KEY = "bl:auth:code:";
    private static final String COOLDOWN_KEY = "bl:auth:code:cooldown:";
    private static final String DAILY_KEY = "bl:auth:code:daily:";
    private static final String FAIL_KEY = "bl:auth:code:fail:";

    private final StringRedisTemplate redis;

    public RedisEmailCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveCode(String email, String code, Duration ttl) {
        redis.opsForValue().set(CODE_KEY + email, code, ttl);
    }

    @Override
    public Optional<String> getCode(String email) {
        return Optional.ofNullable(redis.opsForValue().get(CODE_KEY + email));
    }

    @Override
    public void removeCode(String email) {
        redis.delete(CODE_KEY + email);
    }

    @Override
    public boolean acquireSendSlot(String email, Duration cooldown) {
        Boolean acquired = redis.opsForValue().setIfAbsent(COOLDOWN_KEY + email, "1", cooldown);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public long increaseDailyCount(String email, Duration window) {
        return increaseWithWindow(DAILY_KEY + email + ":" + LocalDate.now(), window);
    }

    @Override
    public long increaseFailCount(String email, Duration window) {
        return increaseWithWindow(FAIL_KEY + email, window);
    }

    @Override
    public void clearFailCount(String email) {
        redis.delete(FAIL_KEY + email);
    }

    /**
     * 计数 +1，首次自增时挂上过期时间。
     * INCR 与 EXPIRE 分两步：极端情况下（自增成功、EXPIRE 前进程崩溃）会留下无过期键，
     * 最坏后果是该邮箱当日额度永久少一次，不影响安全边界，故不引入 Lua。
     */
    private long increaseWithWindow(String key, Duration window) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, window);
        }
        return value == null ? 0L : value;
    }
}
