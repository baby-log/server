package com.babylog.server.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置中心（Nacos）接线的静态守卫（V3-S2 L6 创立，BabyLog S2 按单体形态拍板整改）。
 *
 * <p><b>2026-11-16 S2 口径变更</b>（依据 07 文档 M4 拍板：单体形态<b>不接 Nacos</b>，
 * 仅 {@code -P cloud/-P alibaba} 打包才生效；配置全部内联 yml）：
 * 原测试强制「application.yml 必须有 spring.config.import 的 nacos 项」，与拍板直接冲突、
 * 恒红。整改后两条规则都改为<b>条件式</b>——单体形态没接线是合法的；一旦有人重新接回
 * Nacos，两条旧红线立即重新生效：
 * <ol>
 *     <li>{@code spring.config.import} 若存在，每一项必须带 {@code optional:} 前缀
 *         （少了它，Nacos 不可达/鉴权失败/dataId 不存在都会直接打断启动）；</li>
 *     <li>Nacos 连接信息（username / password / server-addr / namespace / group）若出现，
 *         只能是 {@code ${...}} 占位符——yml 进 git，写明文凭据就是事故。</li>
 * </ol>
 *
 * <p>刻意不用 YAML 解析器：这里要断言的是「原文形态」（前缀、占位符），
 * 解析完反而看不到 {@code optional:} 这样的字面约束。
 */
class NacosConfigImportGuardTest {

    private static final Path YML = Path.of("src/main/resources/application.yml");

    /** 值必须是纯占位符（允许 ${NAME:default} 形态）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{[^}]*}$");

    private static final Pattern NACOS_KEY =
        Pattern.compile("^\\s{8}(username|password|server-addr|namespace|group)\\s*:\\s*(\\S.*)$");

    @Test
    void 配置中心导入若存在必须带optional前缀() throws IOException {
        List<String> imports = collectImports();
        for (String item : imports) {
            assertTrue(item.startsWith("optional:"),
                "spring.config.import 的每一项都必须以 optional: 开头，发现违规项：" + item
                    + "（单体形态可不接配置中心；重新接回时去掉前缀 = Nacos 不可达时直接打断启动）");
        }
    }

    @Test
    void nacos连接信息若存在只能是环境变量占位符() throws IOException {
        List<String> lines = Files.readAllLines(YML, StandardCharsets.UTF_8);
        boolean inNacos = false;
        for (String raw : lines) {
            String line = stripComment(raw);
            if (line.isBlank()) {
                continue;
            }
            int indent = indentOf(line);
            String trimmed = line.trim();
            if (!inNacos) {
                if (indent == 4 && trimmed.equals("nacos:")) {
                    inNacos = true;
                }
                continue;
            }
            if (indent <= 4) {
                inNacos = false;
                continue;
            }
            Matcher m = NACOS_KEY.matcher(line);
            if (m.matches()) {
                String value = m.group(2).trim();
                assertTrue(PLACEHOLDER.matcher(value).matches(),
                    "Nacos 的 " + m.group(1) + " 必须是 ${...} 环境变量占位符，yml 进 git 不得落明文。当前值：" + value);
            }
        }
        // S2 口径：单体形态没有 nacos 段是合法终态，不做存在性断言（见类注释）
    }

    private static List<String> collectImports() throws IOException {
        List<String> imports = new ArrayList<>();
        boolean inImport = false;
        for (String raw : Files.readAllLines(YML, StandardCharsets.UTF_8)) {
            String line = stripComment(raw);
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.equals("import:") && indentOf(line) == 4) {
                inImport = true;
                continue;
            }
            if (inImport) {
                if (trimmed.startsWith("- ")) {
                    imports.add(trimmed.substring(2).trim());
                } else {
                    inImport = false;
                }
            }
        }
        return imports;
    }

    private static String stripComment(String line) {
        int idx = line.indexOf('#');
        return idx < 0 ? line : line.substring(0, idx);
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }
}
