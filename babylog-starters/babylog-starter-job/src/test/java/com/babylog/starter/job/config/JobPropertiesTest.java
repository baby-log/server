package com.babylog.starter.job.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JobProperties configuration binding.
 */
@DisplayName("JobProperties tests")
class JobPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JobPropertiesConfig.class);

    @Test
    @DisplayName("should bind default values")
    void shouldBindDefaultValues() {
        contextRunner.run(ctx -> {
            JobProperties props = ctx.getBean(JobProperties.class);
            assertThat(props.getAdminAddresses()).isNull();
            assertThat(props.getAppName()).isEqualTo("babylog-executor");
            assertThat(props.getPort()).isEqualTo(9999);
            assertThat(props.getLogRetentionDays()).isEqualTo(30);
            assertThat(props.getLogPath()).isEqualTo("./logs/xxl-job");
        });
    }

    @Test
    @DisplayName("should bind custom values")
    void shouldBindCustomValues() {
        contextRunner
                .withPropertyValues(
                        "babylog.job.admin-addresses=http://192.168.1.100:9080/xxl-job-admin",
                        "babylog.job.app-name=custom-app",
                        "babylog.job.access-token=custom-token-123",
                        "babylog.job.port=9998",
                        "babylog.job.log-path=/var/log/xxl-job",
                        "babylog.job.log-retention-days=60"
                )
                .run(ctx -> {
                    JobProperties props = ctx.getBean(JobProperties.class);
                    assertThat(props.getAdminAddresses()).isEqualTo("http://192.168.1.100:9080/xxl-job-admin");
                    assertThat(props.getAppName()).isEqualTo("custom-app");
                    assertThat(props.getAccessToken()).isEqualTo("custom-token-123");
                    assertThat(props.getPort()).isEqualTo(9998);
                    assertThat(props.getLogPath()).isEqualTo("/var/log/xxl-job");
                    assertThat(props.getLogRetentionDays()).isEqualTo(60);
                });
    }

    @Test
    @DisplayName("should bind app-name property")
    void shouldBindAppName() {
        contextRunner
                .withPropertyValues("babylog.job.app-name=test-executor")
                .run(ctx -> {
                    JobProperties props = ctx.getBean(JobProperties.class);
                    assertThat(props.getAppName()).isEqualTo("test-executor");
                });
    }

    @EnableConfigurationProperties(JobProperties.class)
    static class JobPropertiesConfig {
    }
}
