package com.babylog.server.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感配置启动门禁（BabyLog V2 修复链 S2）：密钥/口令/连接串<b>缺失即 fail-fast</b>。
 *
 * <p>背景：M4 拍板单体形态<b>不接 Nacos 配置中心</b>（仅 {@code -P cloud/-P alibaba} 打包才生效），
 * 配置全部内联 yml（私有仓库）。这把「配置中心兜着的最后一道闸」搬到了启动期——
 * 以前 Nacos 缺席时至少还有「必配项校验」，现在配漏了就是<b>静默带病启动</b>：
 * 库连不上、Redis WRONGPASS、COS 签名失败，全部变成运行期故障。
 *
 * <p>口径（与 {@code FieldEncryptKeyGuard} 同款「prod 硬拒 / 其余形态 WARN」）：
 * <ol>
 *   <li><b>prod 命中任一问题 → 拒绝启动</b>（EnvironmentPostProcessor 抛异常，进程非零退出，
 *       在任何一个数据源/Redis Bean 创建<b>之前</b>）；</li>
 *   <li>非 prod 形态 → 逐条 WARN，行为零变化（dev 允许零配置起服，SMTP 未配本来就该 8374 兜底）；</li>
 *   <li>「缺失」= 解析值空白、或值里仍残留 {@code ${...}}（占位符没注入成功，属配置事故）；</li>
 *   <li>功能条件触发型：只在该功能<b>启用</b>时才要求它的密钥——SMTP 配了 host 就要有口令、
 *       file 通道是 s3 就要有 access/secret key、小程序配了 appid 就要有 secret；
 *       库口令与 Redis 口令是无条件必配（prod）；</li>
 *   <li>紧急绕过开关 {@code babylog.startup.sensitive-config-guard-enabled=false}
 *       （prod 可用，每次绕过都留 WARN 痕）；</li>
 *   <li>任何路径都<b>不打印密钥值</b>——报告里只有键名。</li>
 * </ol>
 *
 * <p>判定核心 {@link #evaluate} 是纯函数（便于单测钉死），环境读取与日志在
 * {@link SensitiveConfigEnvironmentPostProcessor}。
 */
public final class SensitiveConfigGuard {

    /** 无条件必配（prod）：主库口令 */
    public static final String MASTER_PASSWORD = "spring.datasource.dynamic.datasource.master.password";
    /** 无条件必配（prod）：Redis 口令 */
    public static final String REDIS_PASSWORD = "spring.data.redis.password";

    /** 功能条件触发型：主键 = 触发条件键，值为该功能必配的密钥键 */
    public static final Map<String, List<String>> CONDITIONAL_REQUIREMENTS = Map.of(
            // 发信功能启用（配了 SMTP host）→ 发件授权码必配；不配则发码端点恒 8375
            "spring.mail.host", List.of("spring.mail.password"),
            // 附件通道走 S3 协议（COS）→ 两枚密钥必配；不配则上传/签名全挂
            "babylog.file.storage-type", List.of("babylog.file.access-key", "babylog.file.secret-key"),
            // 小程序登录配置（appid 存在即视为启用）→ appsecret 必配；不配则 mini login 恒 8395/8396
            "babylog.wechat.miniapp.configs.babylog.appid",
                    List.of("babylog.wechat.miniapp.configs.babylog.secret"));

    /** storage-type 取哪些值时才触发 S3 密钥校验（local/minio 形态不需要这两枚 key） */
    public static final List<String> S3_STORAGE_TYPES = List.of("s3", "cos");

    /** 紧急绕过开关 */
    public static final String GUARD_ENABLED_KEY = "babylog.startup.sensitive-config-guard-enabled";

    /** 触发条件的「启用」判定：该键解析值非空白即视为启用 */
    public static boolean featureEnabled(Environment environment, String triggerKey) {
        return StringUtils.hasText(environment.getProperty(triggerKey));
    }

    /**
     * 纯函数判定：收集全部问题（键级，不含任何值）。
     *
     * @param prodActive      prod profile 是否生效
     * @param guardOn         总开关
     * @param resolved        已解析配置（键 → 值，可为 null 表示未配置）
     * @param s3StorageActive file 通道当前是否 S3 形态（调用方据 storage-type 判定）
     * @return 问题清单（键名 + 原因）；空 = 通过
     */
    public static List<String> evaluate(boolean prodActive, boolean guardOn, Map<String, String> resolved,
                                        boolean s3StorageActive) {
        List<String> problems = new ArrayList<>();
        if (!guardOn) {
            return problems;
        }
        requireNonBlank(problems, resolved, MASTER_PASSWORD, "主库口令缺失");
        requireNonBlank(problems, resolved, REDIS_PASSWORD, "Redis 口令缺失");
        for (Map.Entry<String, List<String>> entry : CONDITIONAL_REQUIREMENTS.entrySet()) {
            String trigger = entry.getKey();
            boolean active = "babylog.file.storage-type".equals(trigger)
                    ? s3StorageActive
                    : StringUtils.hasText(resolved.get(trigger));
            if (!active) {
                continue;
            }
            for (String secretKey : entry.getValue()) {
                requireNonBlank(problems, resolved, secretKey, "功能 " + trigger + " 已启用但其密钥缺失");
            }
        }
        return problems;
    }

    private static void requireNonBlank(List<String> problems, Map<String, String> resolved,
                                        String key, String reason) {
        String value = resolved.get(key);
        if (!StringUtils.hasText(value)) {
            problems.add(key + "（" + reason + "）");
            return;
        }
        if (value.contains("${")) {
            problems.add(key + "（占位符未解析成功：" + reason + "）");
        }
    }

    /**
     * prod 拒绝启动时返回给运维看的说明（含修复指引）；否则返回 null。
     * 报告只含键名，永不回显密钥值。
     */
    public static String rejection(List<String> problems) {
        if (problems.isEmpty()) {
            return null;
        }
        return "敏感配置门禁拦截启动（prod 形态）：\n  - "
                + String.join("\n  - ", problems)
                + "\n请在 application-prod.yml（或更高优先级配置源）补齐上述键后重启；"
                + "确需紧急绕过可临时设置 " + GUARD_ENABLED_KEY + "=false（会留 WARN 痕）。";
    }

    /** 供调用方装配 resolved 映射的便捷工厂（保持插入序，报告稳定可读） */
    public static Map<String, String> resolvedMap(Environment environment, Iterable<String> keys) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String key : keys) {
            resolved.put(key, environment.getProperty(key));
        }
        return resolved;
    }

    private SensitiveConfigGuard() {
    }
}
