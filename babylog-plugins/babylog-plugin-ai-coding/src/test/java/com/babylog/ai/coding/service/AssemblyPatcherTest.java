package com.babylog.ai.coding.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AssemblyPatcher (temp-dir fake framework root).
 */
@DisplayName("AssemblyPatcher unit tests")
class AssemblyPatcherTest {

    private final AssemblyPatcher patcher = new AssemblyPatcher();

    @TempDir
    Path root;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(root.resolve("babylog-plugins"));
        Files.writeString(root.resolve("babylog-plugins/pom.xml"), """
                <project>
                    <modules>
                        <module>babylog-plugin-system</module>
                    </modules>
                </project>
                """);
        Files.createDirectories(root.resolve("babylog-dependencies"));
        Files.writeString(root.resolve("babylog-dependencies/pom.xml"), """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.babylog</groupId>
                                <artifactId>babylog-plugin-system</artifactId>
                                <version>${project.version}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.createDirectories(root.resolve("babylog-admin-server"));
        Files.writeString(root.resolve("babylog-admin-server/pom.xml"), """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>com.babylog</groupId>
                            <artifactId>babylog-plugin-system</artifactId>
                        </dependency>
                    </dependencies>

                    <dependencyManagement>
                        <dependencies>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.createDirectories(root.resolve("babylog-admin-server/src/main/java/com/babylog/server"));
        Files.writeString(root.resolve(
                "babylog-admin-server/src/main/java/com/babylog/server/BabyLogAdminApplication.java"),
                """
                @SpringBootApplication(scanBasePackages = {"com.babylog.server", "com.babylog.system"})
                public class BabyLogAdminApplication {}
                """);
    }

    @Test
    @DisplayName("patch inserts modules / BOM entries / dependency / scanBasePackages")
    void testPatchSuccess() throws Exception {
        List<String> changed = patcher.patch(root, "asset", "资产管理");
        assertEquals(4, changed.size());

        String pluginsPom = Files.readString(root.resolve("babylog-plugins/pom.xml"));
        assertTrue(pluginsPom.contains("<module>babylog-plugin-asset-api</module>"));
        assertTrue(pluginsPom.contains("<module>babylog-plugin-asset</module>"));

        // BOM：双模块版本仲裁插进 dependencyManagement
        String bomPom = Files.readString(root.resolve("babylog-dependencies/pom.xml"));
        assertTrue(bomPom.contains("<artifactId>babylog-plugin-asset-api</artifactId>"));
        assertTrue(bomPom.contains("<artifactId>babylog-plugin-asset</artifactId>"));
        assertTrue(bomPom.indexOf("<artifactId>babylog-plugin-asset</artifactId>")
                < bomPom.indexOf("</dependencyManagement>"));

        // admin-server：依赖必须落在顶层 dependencies（dependencyManagement 之前）
        String adminPom = Files.readString(root.resolve("babylog-admin-server/pom.xml"));
        int depIdx = adminPom.indexOf("<artifactId>babylog-plugin-asset</artifactId>");
        assertTrue(depIdx > 0);
        assertTrue(depIdx < adminPom.indexOf("<dependencyManagement>"),
                "依赖不得落进 dependencyManagement（S42 实测踩坑）");

        String appClass = Files.readString(root.resolve(
                "babylog-admin-server/src/main/java/com/babylog/server/BabyLogAdminApplication.java"));
        assertTrue(appClass.contains("\"com.babylog.asset\""));
        assertTrue(appClass.contains("\"com.babylog.system\", \"com.babylog.asset\""));
    }

    @Test
    @DisplayName("patch is idempotent (second run changes nothing)")
    void testPatchIdempotent() throws Exception {
        patcher.patch(root, "asset", "资产管理");
        String before1 = Files.readString(root.resolve("babylog-plugins/pom.xml"));
        String before2 = Files.readString(root.resolve("babylog-admin-server/pom.xml"));
        String before3 = Files.readString(root.resolve(
                "babylog-admin-server/src/main/java/com/babylog/server/BabyLogAdminApplication.java"));

        List<String> changed = patcher.patch(root, "asset", "资产管理");
        assertTrue(changed.isEmpty());
        assertEquals(before1, Files.readString(root.resolve("babylog-plugins/pom.xml")));
        assertEquals(before2, Files.readString(root.resolve("babylog-admin-server/pom.xml")));
        assertEquals(before3, Files.readString(root.resolve(
                "babylog-admin-server/src/main/java/com/babylog/server/BabyLogAdminApplication.java")));
    }

    @Test
    @DisplayName("resolveFrameworkRoot walks up to dir containing babylog-plugins/pom.xml")
    void testResolveFrameworkRoot() throws Exception {
        Path nested = root.resolve("babylog-admin-server/target");
        Files.createDirectories(nested);
        String original = System.getProperty("user.dir");
        System.setProperty("user.dir", nested.toString());
        try {
            assertEquals(root.toAbsolutePath(), patcher.resolveFrameworkRoot());
        } finally {
            System.setProperty("user.dir", original);
        }
    }
}
