package com.babylog.server.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感配置门禁的接线守卫（S2）：锁死 EnvironmentPostProcessor 的注册形态。
 *
 * <p><b>为什么需要它</b>：Spring Framework 7 的 {@code SpringFactoriesLoader} 默认只读
 * {@code META-INF/spring.factories}——Spring Boot 2.4~3.x 那套
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}
 * 在这里<b>静默失效</b>（类不加载、无报错、门禁形同虚设）。S2 首版就踩了这个坑：
 * 探针实例起服后无任何门禁日志，排查半天才定位到注册文件形态。
 * 两条断言把「正确的文件存在 + 废弃的文件不存在」钉死，防回潮。
 */
class SensitiveConfigWiringTest {

    private static final Path FACTORIES = Path.of("src/main/resources/META-INF/spring.factories");
    private static final Path LEGACY_IMPORTS = Path.of(
            "src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports");

    @Test
    void 门禁必须在springFactories注册() throws IOException {
        assertTrue(Files.exists(FACTORIES),
                "缺少 META-INF/spring.factories —— 敏感配置门禁的 EnvironmentPostProcessor 不会被加载（Spring 7 只读 spring.factories）");
        String content = Files.readString(FACTORIES, StandardCharsets.UTF_8);
        assertTrue(content.contains("org.springframework.boot.env.EnvironmentPostProcessor"),
                "spring.factories 里必须注册 org.springframework.boot.env.EnvironmentPostProcessor 键");
        assertTrue(content.contains(SensitiveConfigEnvironmentPostProcessor.class.getName()),
                "spring.factories 里必须登记 " + SensitiveConfigEnvironmentPostProcessor.class.getName());
    }

    @Test
    void 废弃的imports形态不得存在() {
        assertFalse(Files.exists(LEGACY_IMPORTS),
                "发现 META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports ——"
                        + " Spring Framework 7 下该文件静默失效（门禁不加载且无报错），必须删改用 spring.factories");
    }
}
