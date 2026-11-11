package com.babylog.starter.web.config;

import com.babylog.starter.web.config.properties.EncryptProperties;
import com.babylog.starter.web.encrypt.ApiCryptoService;
import com.babylog.starter.web.encrypt.EncryptKeyController;
import com.babylog.starter.web.encrypt.EncryptRequestBodyAdvice;
import com.babylog.starter.web.encrypt.EncryptResponseBodyAdvice;
import com.babylog.starter.web.encrypt.EncryptSessionManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 接口加解密自动配置：babylog.encrypt.enabled=true 才装配（默认关闭）
 */
@AutoConfiguration
@EnableConfigurationProperties(EncryptProperties.class)
@ConditionalOnProperty(prefix = "babylog.encrypt", name = "enabled", havingValue = "true")
public class EncryptAutoConfiguration {

    @Bean
    public ApiCryptoService apiCryptoService() {
        return new ApiCryptoService();
    }

    @Bean
    public EncryptSessionManager encryptSessionManager() {
        return new EncryptSessionManager();
    }

    @Bean
    public EncryptKeyController encryptKeyController(ApiCryptoService cryptoService,
                                                     EncryptSessionManager sessionManager,
                                                     EncryptProperties properties) {
        return new EncryptKeyController(cryptoService, sessionManager,
                TimeUnit.MINUTES.toMillis(properties.getKeyExpireMinutes()));
    }

    @Bean
    public EncryptRequestBodyAdvice encryptRequestBodyAdvice(ApiCryptoService cryptoService,
                                                             EncryptSessionManager sessionManager) {
        return new EncryptRequestBodyAdvice(cryptoService, sessionManager);
    }

    @Bean
    public EncryptResponseBodyAdvice encryptResponseBodyAdvice(ApiCryptoService cryptoService,
                                                               EncryptSessionManager sessionManager) {
        return new EncryptResponseBodyAdvice(cryptoService, sessionManager);
    }
}
