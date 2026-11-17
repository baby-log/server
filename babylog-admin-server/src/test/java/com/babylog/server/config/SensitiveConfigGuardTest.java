package com.babylog.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SensitiveConfigGuard} 判定口径钉死（S2：敏感配置启动门禁）。
 */
class SensitiveConfigGuardTest {

    private static Map<String, String> allBlank() {
        Map<String, String> resolved = new HashMap<>();
        resolved.put(SensitiveConfigGuard.MASTER_PASSWORD, null);
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, null);
        resolved.put("babylog.file.storage-type", null);
        SensitiveConfigGuard.CONDITIONAL_REQUIREMENTS.forEach((trigger, keys) -> {
            resolved.put(trigger, null);
            keys.forEach(k -> resolved.put(k, null));
        });
        return resolved;
    }

    @Test
    void 全配齐时通过() {
        Map<String, String> resolved = allBlank();
        resolved.put(SensitiveConfigGuard.MASTER_PASSWORD, "x");
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        assertTrue(SensitiveConfigGuard.evaluate(true, true, resolved, false).isEmpty());
    }

    @Test
    void prod缺主库口令即问题() {
        Map<String, String> resolved = allBlank();
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        List<String> problems = SensitiveConfigGuard.evaluate(true, true, resolved, false);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).startsWith(SensitiveConfigGuard.MASTER_PASSWORD));
    }

    @Test
    void 值残留未解析占位符视为缺失() {
        Map<String, String> resolved = allBlank();
        resolved.put(SensitiveConfigGuard.MASTER_PASSWORD, "${BABYLOG_DB_PASSWORD}");
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        List<String> problems = SensitiveConfigGuard.evaluate(true, true, resolved, false);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("占位符未解析成功"));
    }

    @Test
    void 功能未启用时其密钥不校验() {
        Map<String, String> resolved = allBlank();
        resolved.put(SensitiveConfigGuard.MASTER_PASSWORD, "x");
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        // SMTP host / appid / s3 全未启用 → 对应密钥缺失不算问题
        assertTrue(SensitiveConfigGuard.evaluate(true, true, resolved, false).isEmpty());
    }

    @Test
    void smtp配了host就要口令() {
        Map<String, String> resolved = allBlank();
        resolved.put(SensitiveConfigGuard.MASTER_PASSWORD, "x");
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        resolved.put("spring.mail.host", "smtp.example.com");
        List<String> problems = SensitiveConfigGuard.evaluate(true, true, resolved, false);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).startsWith("spring.mail.password"));
    }

    @Test
    void s3形态要求两枚密钥_minio形态不要求() {
        Map<String, String> resolved = allBlank();
        resolved.put(SensitiveConfigGuard.MASTER_PASSWORD, "x");
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        resolved.put("babylog.file.storage-type", "s3");
        assertEquals(2, SensitiveConfigGuard.evaluate(true, true, resolved, true).size());

        Map<String, String> minio = allBlank();
        minio.put(SensitiveConfigGuard.MASTER_PASSWORD, "x");
        minio.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        minio.put("babylog.file.storage-type", "minio");
        assertTrue(SensitiveConfigGuard.evaluate(true, true, minio, false).isEmpty());
    }

    @Test
    void 小程序配了appid就要secret() {
        Map<String, String> resolved = allBlank();
        resolved.put(SensitiveConfigGuard.MASTER_PASSWORD, "x");
        resolved.put(SensitiveConfigGuard.REDIS_PASSWORD, "x");
        resolved.put("babylog.wechat.miniapp.configs.babylog.appid", "wx123");
        List<String> problems = SensitiveConfigGuard.evaluate(true, true, resolved, false);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).startsWith("babylog.wechat.miniapp.configs.babylog.secret"));
    }

    @Test
    void 总开关关闭时一律放行() {
        Map<String, String> resolved = allBlank();
        assertTrue(SensitiveConfigGuard.evaluate(true, false, resolved, true).isEmpty());
    }

    @Test
    void rejection只在有问题时给出说明且不含密钥值() {
        assertNull(SensitiveConfigGuard.rejection(List.of()));
        String detail = SensitiveConfigGuard.rejection(List.of(SensitiveConfigGuard.MASTER_PASSWORD + "（主库口令缺失）"));
        assertTrue(detail.contains(SensitiveConfigGuard.MASTER_PASSWORD));
        assertTrue(detail.contains(SensitiveConfigGuard.GUARD_ENABLED_KEY));
    }

    @Test
    void featureEnabled按非空白判定() {
        MockEnvironment env = new MockEnvironment();
        assertFalse(SensitiveConfigGuard.featureEnabled(env, "spring.mail.host"));
        env.setProperty("spring.mail.host", "  ");
        assertFalse(SensitiveConfigGuard.featureEnabled(env, "spring.mail.host"));
        env.setProperty("spring.mail.host", "smtp.example.com");
        assertTrue(SensitiveConfigGuard.featureEnabled(env, "spring.mail.host"));
    }
}
