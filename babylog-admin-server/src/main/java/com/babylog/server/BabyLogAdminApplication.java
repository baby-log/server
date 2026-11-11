package com.babylog.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BabyLog 管理端单体启动器
 *
 * 扫描范围约定（S8 决议）：
 * - Starter（com.babylog.starter.*）只走 AutoConfiguration.imports 自动装配，严禁组件扫描
 * - 每个 Plugin 的根包在两处显式登记；新增 Plugin 时各加一行（S13 范式将固化此约定）：
 *   ① scanBasePackages：组件扫描（Controller/Service/Config）
 *   ② @AutoConfigurationPackage.basePackages：MyBatis-Plus @Mapper 接口自动扫描的包来源
 *   注意：两者相互独立，scanBasePackages 不会自动带进 AutoConfigurationPackages（S8 踩坑 2）
 */
@SpringBootApplication(scanBasePackages = {"com.babylog.server", "com.babylog.system", "com.babylog.message", "com.babylog.file", "com.babylog.ai", "com.babylog.generator", "com.babylog.monitor", "com.babylog.workflow", "com.babylog.ai.kb", "com.babylog.docsync", "com.babylog.migration", "com.babylog.mind"})
@AutoConfigurationPackage(basePackages = {"com.babylog.server", "com.babylog.system", "com.babylog.message", "com.babylog.file", "com.babylog.ai", "com.babylog.generator", "com.babylog.monitor", "com.babylog.workflow", "com.babylog.ai.kb", "com.babylog.docsync", "com.babylog.migration", "com.babylog.mind"})
public class BabyLogAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BabyLogAdminApplication.class, args);
    }
}
