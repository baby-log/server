package com.babylog.server.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SensitiveConfigGuard} 的启动期接线：在 ConfigData 加载<b>之后</b>、
 * 上下文创建<b>之前</b>执行——prod 缺密钥时进程直接非零退出，数据源/Redis 等
 * Bean 都来不及初始化（fail-fast 的最深可及位置）。
 *
 * <p>非 prod 形态只打 WARN（dev 零配置起服不受影响，与 FieldEncryptKeyGuard 同口径）。
 * 日志走 {@link DeferredLogFactory}：post-processor 执行时日志系统尚未初始化。
 */
public class SensitiveConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private final Log log;

    public SensitiveConfigEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(getClass());
    }

    @Override
    public int getOrder() {
        // ConfigData（yml/profile 激活/占位符解析）之后立即校验，最早的可判定时机
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean guardOn = environment.getProperty(SensitiveConfigGuard.GUARD_ENABLED_KEY, Boolean.class, true);
        if (!guardOn) {
            log.warn("[敏感配置门禁] 已被 " + SensitiveConfigGuard.GUARD_ENABLED_KEY
                    + "=false 显式摘除——本次启动不校验密钥/口令缺失， prod 风险自负。");
            return;
        }
        boolean prodActive = environment.matchesProfiles("prod");
        boolean s3StorageActive = isS3StorageActive(environment);
        List<String> problems = SensitiveConfigGuard.evaluate(
                prodActive, true, resolveAll(environment), s3StorageActive);
        if (problems.isEmpty()) {
            log.info("[敏感配置门禁] 通过（形态=" + (prodActive ? "prod" : "非prod") + "）。");
            return;
        }
        String detail = SensitiveConfigGuard.rejection(problems);
        if (prodActive) {
            throw new IllegalStateException(detail);
        }
        log.warn("[敏感配置门禁] 非 prod 形态，仅告警不拦截：\n" + detail);
    }

    private static boolean isS3StorageActive(Environment environment) {
        String storageType = environment.getProperty("babylog.file.storage-type");
        if (storageType == null) {
            return false;
        }
        return SensitiveConfigGuard.S3_STORAGE_TYPES.contains(storageType.trim().toLowerCase());
    }

    private static java.util.Map<String, String> resolveAll(Environment environment) {
        List<String> keys = new ArrayList<>(List.of(
                SensitiveConfigGuard.MASTER_PASSWORD,
                SensitiveConfigGuard.REDIS_PASSWORD,
                "babylog.file.storage-type"));
        SensitiveConfigGuard.CONDITIONAL_REQUIREMENTS.forEach((trigger, secretKeys) -> {
            keys.add(trigger);
            keys.addAll(secretKeys);
        });
        return SensitiveConfigGuard.resolvedMap(environment, keys);
    }
}
