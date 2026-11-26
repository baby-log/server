package com.babylog.biz.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 参数中心（{@code sys_config}）只读通道（K6-S / M76，龙哥 2026-11-25 门禁 C 授权）。
 *
 * <p><b>为什么在 biz 内自建，而不是注入 system 插件的 {@code ConfigService}</b>：
 * 该 Service 位于 system <b>实现层</b>（{@code com.babylog.system.service}），
 * biz 直接 import 会同时踩 ArchUnit A1/A2 与 C1「跨插件只走契约」红线；
 * 全仓也没有 {@code IConfigFacade} 契约。这里只用 Spring 自带的 {@link JdbcTemplate}
 * 做<b>单表只读</b>查询，不 import 任何 system 包、不建 sys_config 的实体映射（不写表映射注解）、
 * 不写 Mapper XML、SQL 用 {@code ?} 占位不与 bl_ 表联表，故不触发 A3/A5/A8/A12/C1/C4。
 * 同形态先例见 {@code AdminConsoleService}（JdbcTemplate 只读查 sys_user）。
 *
 * <p><b>实时性</b>：每次调用都查库、无缓存，运营在「系统工具 - 参数设置」页改
 * {@code config_value} 后下一次问答即时生效，无需重启。
 *
 * <p><b>架构欠账（移交登记）</b>：微服务化拆库后此直查会失效，届时按项目
 * Facade 体例补 {@code IConfigFacade} 契约替换本类。当前是单体 fat jar，零新 SDK / 零新中间件。
 * {@code sys_config} 在 {@code TenantProperties.BUILTIN_IGNORE_TABLES} 里是全局共享表，无租户隔离，直查语义安全。
 */
@Component
@RequiredArgsConstructor
public class AiQuotaConfigReader {

    private final JdbcTemplate jdbc;

    /**
     * 读参数值；键不存在 / 无有效行返回 {@code null}（由调用方回落 yml）。
     *
     * <p>显式带 {@code deleted = 0}：本类不经 MyBatis-Plus，逻辑删除要自己补条件。
     */
    public String read(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return null;
        }
        List<String> values = jdbc.queryForList(
                "SELECT config_value FROM sys_config WHERE config_key = ? AND deleted = 0",
                String.class, configKey);
        return values.isEmpty() ? null : values.get(0);
    }
}
