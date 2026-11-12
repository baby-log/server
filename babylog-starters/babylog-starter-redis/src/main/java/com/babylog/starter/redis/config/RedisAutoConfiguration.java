package com.babylog.starter.redis.config;

import com.babylog.starter.redis.cache.TenantCacheKeyGenerator;
import com.babylog.starter.redis.checker.RedisRepeatSubmitChecker;
import com.babylog.starter.redis.ratelimit.RateLimitService;
import com.babylog.starter.web.checker.RepeatSubmitChecker;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * Redis 分布式能力自动配置：
 * Redisson / Lock4j 由各自 starter 自动装配，本类注册框架增强 Bean。
 * 注册 RepeatSubmitChecker 后，starter-web 的幂等拦截器自动激活（条件装配闭环）。
 *
 * <p><b>装配顺序修复（M15，PROGRESS.md 会话 M14 拍板 P3，红线豁免一次）</b>：
 * 类级 {@code @ConditionalOnBean(RedissonClient)} 在自动配置<b>排序后</b>求值——
 * 默认按类名字典序 {@code com.babylog.starter.redis} 排在 {@code org.redisson} 之前，
 * 求值时 Redisson 自家自动配置（4.x 为 {@code RedissonAutoConfigurationV4}）尚未注册
 * 客户端 Bean，条件不成立 → 本类整类被跳过 → RepeatSubmitChecker 缺失 →
 * starter-web 的 {@code @RepeatSubmit} 拦截器静默失效（M10 发现，--debug condition
 * report 实锤）。修法即本行 {@code after}：显式声明在 Redisson 自动配置之后处理。
 * 勿把条件挪回方法级——方法级条件同样在本类被处理时求值，时序问题不变。
 */
@AutoConfiguration(after = {RedissonAutoConfigurationV2.class, RedissonAutoConfigurationV4.class})
@EnableCaching
@ConditionalOnBean(RedissonClient.class)
public class RedisAutoConfiguration {

    /**
     * 幂等判定器 Redis 实现（激活 starter-web 的 @RepeatSubmit）
     */
    @Bean
    @ConditionalOnMissingBean(RepeatSubmitChecker.class)
    public RepeatSubmitChecker repeatSubmitChecker(RedissonClient redissonClient) {
        return new RedisRepeatSubmitChecker(redissonClient);
    }

    /**
     * 租户维度缓存 key 生成器
     */
    @Bean
    public TenantCacheKeyGenerator tenantCacheKeyGenerator() {
        return new TenantCacheKeyGenerator();
    }

    /**
     * 缓存管理器兜底（M15 装配顺序修复的连带件，与 @RepeatSubmit 同一缺陷家族）：
     *
     * <p>本类在修复前<b>整类被跳过</b>（见类 javadoc），所以 {@code @EnableCaching}
     * 从框架第一天起就没生效过、全应用也从没有过 CacheManager。修复后类真正装配，
     * {@code @EnableCaching} 激活的缓存拦截器在 afterSingletonsInstantiated 阶段
     * 索取 CacheManager，缺它 = 应用起不来（dev 起服实测，APPLICATION FAILED TO START）。
     *
     * <p>这里给的是 Spring Boot 无缓存组件时的同款兜底（进程内 ConcurrentMap），
     * 单节点部署语义不变；应用要换 Redis 实现时自带一个 CacheManager Bean 即可
     * （ConditionalOnMissingBean 让位）。Boot 自家的 CacheAutoConfiguration 因
     * 排序在本类之后、条件求值时序不可靠，不能指望它兜——这就是必须显式给的原因。
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

    /**
     * 分布式限流器
     */
    @Bean
    public RateLimitService rateLimitService(RedissonClient redissonClient) {
        return new RateLimitService(redissonClient);
    }
}
