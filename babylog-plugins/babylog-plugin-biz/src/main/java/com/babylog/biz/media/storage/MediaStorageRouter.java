package com.babylog.biz.media.storage;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储后端路由（K2 / M69，契约 {@code 24} ③-3）。
 *
 * <p><b>按行路由</b>：读 / 下载 / 删除 / 淘汰都取行内 {@code storage_backend} 找适配器，
 * 而不是取「当前默认后端」——这正是「配置变更只对新写入生效」的实现：
 * 老行的对象键与后端一起固化在行里，改配置不会让它突然读不到。
 *
 * <p>新写入才用 {@link #writeBackend()}；存量搬迁只能走显式手动迁移工具
 * （{@code MediaMigrationService}），禁止运行时自动全量重定位。
 */
public class MediaStorageRouter {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Map<String, MediaStorageAdapter> byBackend;
    private final MediaStorageProperties properties;

    public MediaStorageRouter(List<MediaStorageAdapter> adapters, MediaStorageProperties properties) {
        this.properties = properties;
        Map<String, MediaStorageAdapter> map = new HashMap<>();
        for (MediaStorageAdapter adapter : adapters) {
            map.put(adapter.backend(), adapter);
        }
        this.byBackend = Map.copyOf(map);
    }

    /** 按行内后端标识路由；标识不在注册表内 ⇒ 8422（配置漂移 / 迁移中断兜底） */
    public MediaStorageAdapter adapterFor(String backend) {
        MediaStorageAdapter adapter = backend == null ? null : byBackend.get(backend);
        if (adapter == null) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_BACKEND_UNKNOWN);
        }
        return adapter;
    }

    /** 新写入使用的后端（配置可变更，只影响此后新写入） */
    public String writeBackend() {
        String backend = properties.defaultBackend();
        if (!byBackend.containsKey(backend)) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_BACKEND_UNKNOWN);
        }
        return backend;
    }

    /** 已注册的后端标识（诊断端点用） */
    public java.util.Set<String> registeredBackends() {
        return byBackend.keySet();
    }

    /** 生成新对象键：{keyPrefix}/{yyyyMM}/{childId}/{snowflake}.{ext}；不含后端标识（后端单独落列） */
    public String newObjectKey(long childId, String extension) {
        String ext = extension == null || extension.isBlank() ? "bin" : sanitizeExt(extension);
        return properties.keyPrefix() + "/" + LocalDate.now().format(MONTH) + "/"
                + childId + "/" + IdWorker.getId() + "." + ext;
    }

    private static String sanitizeExt(String ext) {
        String out = ext.trim().toLowerCase(java.util.Locale.ROOT);
        if (out.startsWith(".")) {
            out = out.substring(1);
        }
        // 只保留字母数字，避免把 mime / 文件名里的分隔符带进对象键
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.length() && sb.length() < 12; i++) {
            char c = out.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "bin" : sb.toString();
    }
}
