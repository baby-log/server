package com.babylog.biz.auth.store;

import java.time.Duration;
import java.util.Optional;

/**
 * 邮箱验证码存储：发码、取码、冷却占位、日配额、错误计数。
 *
 * <p>抽成接口的目的很实际：验证码逻辑（限流判定、错误作废、时序）是本会话最需要单测的部分，
 * 而 Redis 不该进单测。生产用 {@link RedisEmailCodeStore}，测试用内存实现。
 */
public interface EmailCodeStore {

    /** 写入验证码（覆盖旧码，重置有效期） */
    void saveCode(String email, String code, Duration ttl);

    /** 取验证码，不存在返回 empty */
    Optional<String> getCode(String email);

    /** 删除验证码（校验通过即消费掉；错误次数超限时作废） */
    void removeCode(String email);

    /**
     * 占用一次发送名额（SETNX 语义：冷却窗口内只有第一次成功）
     *
     * @return true = 拿到名额可以发，false = 冷却中
     */
    boolean acquireSendSlot(String email, Duration cooldown);

    /**
     * 当日发送计数 +1
     *
     * @return 自增后的值（首次自增时设置窗口过期时间）
     */
    long increaseDailyCount(String email, Duration window);

    /**
     * 单个验证码的错误计数 +1
     *
     * @return 自增后的值
     */
    long increaseFailCount(String email, Duration window);

    /** 清零错误计数（校验通过、或重新发码时） */
    void clearFailCount(String email);
}
