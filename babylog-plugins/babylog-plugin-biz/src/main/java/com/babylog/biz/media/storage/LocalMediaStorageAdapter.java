package com.babylog.biz.media.storage;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件系统后端（dev / 自托管）。
 *
 * <p>零第三方依赖：{@code java.nio} 直接读写。objectKey 视为根目录内的相对路径，
 * 用 {@code normalize().startsWith(root)} 阻断路径穿越（上传键由服务端生成，仍做防御）。
 *
 * <p>delete 幂等：{@code Files.deleteIfExists} 对不存在的键直接返回 false，
 * 与 S3 DeleteObject 语义一致 ⇒ 淘汰任务「先物理删再逻辑删」失败后可安全重试。
 */
public class LocalMediaStorageAdapter implements MediaStorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(LocalMediaStorageAdapter.class);

    private final Path root;

    public LocalMediaStorageAdapter(String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            // 不阻断启动：写入时会再次尝试建目录；此处只登记，避免 dev 只读环境起不来
            log.warn("[BabyLog][media] 本地存储根目录创建失败，将在首次写入时重试 root={} cause={}",
                    root, e.getMessage());
        }
    }

    @Override
    public String backend() {
        return "local";
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolve(objectKey));
    }

    @Override
    public void put(String objectKey, byte[] data, String mimeType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".media-", ".tmp");
            try {
                Files.write(tmp, data);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveFailure) {
                // ATOMIC_MOVE 在部分文件系统不支持时降级为普通 REPLACE
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            log.error("[BabyLog][media] 本地写入失败 objectKey={}", objectKey, e);
            throw new ServiceException(BabyLogErrorCode.MEDIA_STORAGE_IO_FAILED);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException e) {
            log.error("[BabyLog][media] 本地读取失败 objectKey={}", objectKey, e);
            throw new ServiceException(BabyLogErrorCode.MEDIA_STORAGE_IO_FAILED);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            log.error("[BabyLog][media] 本地删除失败 objectKey={}", objectKey, e);
            throw new ServiceException(BabyLogErrorCode.MEDIA_STORAGE_IO_FAILED);
        }
    }

    /** 服务端生成的 objectKey 也要防穿越；越界一律 8426 而不是拼出根外路径 */
    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_STORAGE_IO_FAILED);
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_STORAGE_IO_FAILED);
        }
        return target;
    }
}
