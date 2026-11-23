package com.babylog.biz.media.storage;

import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地后端实测（K2 / M69 验证清单「至少本地 + COS 两个在 dev 实测」的本地侧）：
 * 用 JUnit {@code @TempDir} 起真实临时目录，跑 put/get/exists/delete 与路径穿越防御。
 */
class LocalMediaStorageAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void putGetExistsDeleteRoundTrip() {
        LocalMediaStorageAdapter adapter = new LocalMediaStorageAdapter(tempDir.toString());
        String key = "babylog-media/202611/42/12345.png";
        byte[] payload = {1, 2, 3, 4};

        assertThat(adapter.exists(key)).isFalse();
        adapter.put(key, payload, "image/png");
        assertThat(adapter.exists(key)).isTrue();
        assertThat(adapter.get(key)).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);

        adapter.delete(key);
        assertThat(adapter.exists(key)).isFalse();
        // delete 幂等：S3 DeleteObject 同款语义，淘汰任务失败可安全重试
        adapter.delete(key);
    }

    @Test
    void putCreatesNestedDirectories() {
        LocalMediaStorageAdapter adapter = new LocalMediaStorageAdapter(tempDir.toString());
        adapter.put("a/b/c/d.bin", new byte[]{9}, "application/octet-stream");
        assertThat(tempDir.resolve("a/b/c/d.bin")).isRegularFile();
    }

    @Test
    void pathTraversalIsRejectedNotEscaped() {
        LocalMediaStorageAdapter adapter = new LocalMediaStorageAdapter(tempDir.toString());
        assertThatThrownBy(() -> adapter.get("../../etc/passwd"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8426));
        assertThatThrownBy(() -> adapter.put("../outside.bin", new byte[]{1}, null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void blankObjectKeyIsRejected() {
        LocalMediaStorageAdapter adapter = new LocalMediaStorageAdapter(tempDir.toString());
        assertThatThrownBy(() -> adapter.get("  "))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8426));
    }

    @Test
    void overwriteUsesAtomicMoveSemantics() {
        LocalMediaStorageAdapter adapter = new LocalMediaStorageAdapter(tempDir.toString());
        String key = "x/1.bin";
        adapter.put(key, new byte[]{1}, null);
        adapter.put(key, new byte[]{2, 3}, null);
        assertThat(adapter.get(key)).containsExactly((byte) 2, (byte) 3);
    }
}
