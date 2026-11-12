package com.babylog.biz.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 认证配置装配：启动类没有 {@code @ConfigurationPropertiesScan}，
 * 插件侧显式 {@code @EnableConfigurationProperties} 引入（与框架既有做法一致）。
 */
@Configuration
@EnableConfigurationProperties(BabyLogAuthProperties.class)
public class BabyLogAuthConfiguration {
}
