package com.babylog.biz.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 额度配置装配（K5 / M73）。
 *
 * <p>启动类没有 {@code @ConfigurationPropertiesScan}，插件侧显式 {@code @EnableConfigurationProperties}
 * 引入（与 {@code BabyLogAuthConfiguration} / {@code BabyLogDeviceBindConfiguration} 同款做法）。
 *
 * <p>本类不产生任何 bean 之外的副作用：AI 能力本身走 {@code plugin-ai} 的 {@code IAiFacade} 契约
 * （跨插件只碰 api 包），<b>不在这里装配 ChatClient、不引新 SDK</b>。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BabyLogAiQuotaProperties.class)
public class BabyLogAiConfiguration {
}
