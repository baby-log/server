package com.babylog.starter.redis.config;

import com.babylog.starter.web.checker.RepeatSubmitChecker;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装配顺序回归锁（M15 修复 @RepeatSubmit 静默失效）：
 *
 * <p>缺陷形态：类级 {@code @ConditionalOnBean(RedissonClient)} 默认按类名字典序在
 * Redisson 自家自动配置之前求值 → 条件不成立 → 整类跳过 → RepeatSubmitChecker 缺失。
 * 修复形态：{@code @AutoConfiguration(after = RedissonAutoConfigurationV2/V4)}。
 *
 * <p>锁两层：①注解层——after 必须显式列出 Redisson 两个自动配置类（防止后人删掉这行
 * 又回到字典序时序坑）；②行为层——有 RedissonClient 时 Checker 注册、没有时整类跳过。
 * 真实排序效果由 Spring Boot 自动配置排序器保证，这里不再起真 Redis。
 */
class RedisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class));

    @Test
    void autoConfigurationMustBeOrderedAfterRedisson() {
        AutoConfiguration annotation = RedisAutoConfiguration.class.getAnnotation(AutoConfiguration.class);
        assertNotNull(annotation, "RedisAutoConfiguration 必须是 @AutoConfiguration");

        List<String> after = Arrays.stream(annotation.after())
                .map(Class::getName)
                .toList();
        // 4.x 注册客户端的是 V4；V2 一并声明（不同 Boot 版本/降级场景兜底），两行都在才稳
        assertTrue(after.contains("org.redisson.spring.starter.RedissonAutoConfigurationV4"),
                "after 必须包含 RedissonAutoConfigurationV4，否则 @ConditionalOnBean(RedissonClient) "
                        + "按字典序先于 Redisson 求值，整类被跳过、@RepeatSubmit 静默失效（M14 P3 修复回归锁）");
        assertTrue(after.contains("org.redisson.spring.starter.RedissonAutoConfigurationV2"),
                "after 必须包含 RedissonAutoConfigurationV2（Spring Boot 2 形态兜底）");
    }

    @Test
    void repeatSubmitCheckerRegisteredWhenRedissonClientPresent() {
        runner.withBean("redissonClient", RedissonClient.class, () -> Mockito.mock(RedissonClient.class))
                .run(context -> {
                    assertTrue(context.containsBean("repeatSubmitChecker"), "有 RedissonClient 时 Checker 必须注册");
                    RepeatSubmitChecker checker = context.getBean(RepeatSubmitChecker.class);
                    assertNotNull(checker, "RepeatSubmitChecker 类型可解析");
                    // @EnableCaching 激活后必须有 CacheManager 兜底，否则应用起不来
                    // （M15 起服实测：NoSuchBeanDefinitionException: CacheManager → APPLICATION FAILED）
                    assertNotNull(context.getBean(CacheManager.class), "CacheManager 必须随类装配（ConcurrentMap 兜底）");
                });
    }

    @Test
    void wholeClassSkippedWhenRedissonClientAbsent() {
        runner.run((ConfigurableApplicationContext context) -> {
            // 没有 RedissonClient（未引 redis starter 的场景）整类跳过，不给应用塞半套 Redis 能力
            assertFalse(context.containsBean("repeatSubmitChecker"));
            assertFalse(context.containsBean("rateLimitService"));
            assertFalse(context.containsBean("cacheManager"));
        });
    }
}
