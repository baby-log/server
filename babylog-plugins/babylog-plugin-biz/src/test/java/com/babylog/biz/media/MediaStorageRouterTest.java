package com.babylog.biz.media.storage;

import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 存储后端路由单测（K2 / M69）：按行路由、配置变更只影响新写入、对象键生成。
 * 纯对象构造，不起 Spring 上下文、不连库。
 */
class MediaStorageRouterTest {

    private final Environment env = mock(Environment.class);
    private final MediaStorageProperties properties = new MediaStorageProperties(env);

    private MediaStorageRouter router() {
        return new MediaStorageRouter(List.of(stub("local"), stub("cos"), stub("oss")), properties);
    }

    @Test
    void writeBackendFollowsConfiguredDefault() {
        when(env.getProperty("babylog.media.default-backend")).thenReturn("cos");
        assertThat(router().writeBackend()).isEqualTo("cos");
    }

    @Test
    void writeBackendDefaultsToLocalWithZeroConfig() {
        assertThat(router().writeBackend()).isEqualTo("local");
    }

    @Test
    void adapterForRoutesByRowBackend() {
        MediaStorageRouter router = router();
        assertThat(router.adapterFor("local").backend()).isEqualTo("local");
        assertThat(router.adapterFor("cos").backend()).isEqualTo("cos");
        assertThat(router.adapterFor("oss").backend()).isEqualTo("oss");
    }

    @Test
    void adapterForUnknownBackendThrows8422() {
        assertThatThrownBy(() -> router().adapterFor("unknown"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8422));
    }

    @Test
    void writeBackendWithUnknownConfigThrows8422() {
        when(env.getProperty("babylog.media.default-backend")).thenReturn("unknown");
        assertThatThrownBy(() -> router().writeBackend())
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8422));
    }

    @Test
    void newObjectKeyHasPrefixMonthChildAndSanitizedExtension() {
        String key = router().newObjectKey(42L, "png");
        assertThat(key).startsWith("babylog-media/");
        assertThat(key).contains("/42/");
        assertThat(key).endsWith(".png");
        assertThat(key).doesNotContain("//");
    }

    @Test
    void newObjectKeyRejectsPathTraversalInExtension() {
        String key = router().newObjectKey(42L, "../../etc/passwd");
        assertThat(key).doesNotContain("..");
        assertThat(key).doesNotContain("/etc");
    }

    @Test
    void registeredBackendsContainsAllThree() {
        assertThat(router().registeredBackends())
                .containsExactlyInAnyOrder("local", "cos", "oss");
    }

    private static MediaStorageAdapter stub(String backend) {
        return new MediaStorageAdapter() {
            @Override
            public String backend() {
                return backend;
            }

            @Override
            public boolean exists(String objectKey) {
                return false;
            }

            @Override
            public void put(String objectKey, byte[] data, String mimeType) {
            }

            @Override
            public byte[] get(String objectKey) {
                return new byte[0];
            }

            @Override
            public void delete(String objectKey) {
            }
        };
    }
}
