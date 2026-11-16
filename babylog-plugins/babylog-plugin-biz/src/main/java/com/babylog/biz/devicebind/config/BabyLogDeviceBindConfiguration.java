package com.babylog.biz.devicebind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BabyLogDeviceBindProperties.class)
public class BabyLogDeviceBindConfiguration {
}
