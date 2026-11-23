package com.babylog.biz.media.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 媒体存储配置（K2 / M69）。
 *
 * <p><b>零 yml 改动</b>：COS 侧复用既有 {@code babylog.file.*} 配置键（file 插件
 * {@code storage-type=s3 vendor=cos} 的真实 bucket），只在其上叠加可选的
 * {@code babylog.media.*} 覆盖；OSS 走同一 S3 兼容协议，读独立的
 * {@code babylog.media.oss.*}（未配置即「后端不可用」，不阻断启动）。
 *
 * <p>凭据只在进程内由 Environment 读取用于构造 SDK，<b>不落日志、不回显、不写文件</b>。
 *
 * <p>「路径可变更」：{@code keyPrefix} / {@code defaultBackend} 只参与<b>新写入</b>的
 * objectKey 生成与后端选择；已落库的行自带 {@code storage_backend} + {@code object_key}，
 * 按行路由 ⇒ 改配置不会隐式重定位存量对象（24 ③-3）。
 */
@Component
public class MediaStorageProperties {

    private final Environment env;

    public MediaStorageProperties(Environment env) {
        this.env = env;
    }

    /** S3 兼容后端的一组连接参数（COS / OSS 共用一个适配器类，只是配置不同） */
    public record S3Config(String backend, String endpoint, String region, String accessKey,
                           String secretKey, String bucket, boolean pathStyle) {
        public boolean configured() {
            return notBlank(endpoint) && notBlank(accessKey) && notBlank(secretKey) && notBlank(bucket);
        }

        private static boolean notBlank(String v) {
            return v != null && !v.isBlank();
        }
    }

    /** 新写入默认后端：local / cos / oss（dev 缺省 local，起服即可实测） */
    public String defaultBackend() {
        return firstOr("local", "babylog.media.default-backend", "babylog.media.defaultBackend");
    }

    /** 本地后端根目录（dev / 自托管） */
    public String localDir() {
        return firstOr(System.getProperty("java.io.tmpdir", "/tmp") + "/babylog-media",
                "babylog.media.local-dir", "babylog.media.localDir");
    }

    /** 对象键前缀；只影响新写入 */
    public String keyPrefix() {
        return stripSlashes(firstOr("babylog-media", "babylog.media.key-prefix", "babylog.media.keyPrefix"));
    }

    /** 每宝宝初始容量（字节），首次上传时自动建配额行 */
    public long defaultQuotaBytes() {
        return longOr(1024L * 1024 * 1024, "babylog.media.default-quota-bytes", "babylog.media.defaultQuotaBytes");
    }

    /** 单对象上传上限（字节） */
    public long maxObjectBytes() {
        return longOr(20L * 1024 * 1024, "babylog.media.max-object-bytes", "babylog.media.maxObjectBytes");
    }

    /** 已确认媒体保留时长（小时，24 ④：2h；允许误差） */
    public long confirmedRetentionHours() {
        return longOr(2L, "babylog.media.retention.confirmed-hours", "babylog.media.retention.confirmedHours");
    }

    /** 未确认媒体保留时长（天，24 ④：3 天；允许误差） */
    public long unconfirmedRetentionDays() {
        return longOr(3L, "babylog.media.retention.unconfirmed-days", "babylog.media.retention.unconfirmedDays");
    }

    /** 淘汰 / 迁移单批处理上限（避免一次全表扫描） */
    public int batchSize() {
        return (int) longOr(200L, "babylog.media.retention.batch-size", "babylog.media.retention.batchSize");
    }

    /** COS：优先 babylog.media.cos.*，回落既有 babylog.file.* */
    public S3Config cosConfig() {
        return new S3Config("cos",
                first("babylog.media.cos.endpoint", "babylog.media.cos-endpoint", "babylog.file.endpoint"),
                firstOr("us-east-1", "babylog.media.cos.region", "babylog.media.cos-region", "babylog.file.region"),
                first("babylog.media.cos.access-key", "babylog.media.cos.accessKey",
                        "babylog.file.access-key", "babylog.file.accessKey"),
                first("babylog.media.cos.secret-key", "babylog.media.cos.secretKey",
                        "babylog.file.secret-key", "babylog.file.secretKey"),
                firstOr("babylog", "babylog.media.cos.bucket", "babylog.media.cos-bucket", "babylog.file.bucket"),
                boolOr(false, "babylog.media.cos.path-style", "babylog.media.cos.pathStyle", "babylog.file.path-style"));
    }

    /** OSS：同一 S3 兼容协议，独立 endpoint / 凭据 / bucket（未配置 ⇒ 后端不可用） */
    public S3Config ossConfig() {
        return new S3Config("oss",
                first("babylog.media.oss.endpoint", "babylog.media.oss-endpoint"),
                firstOr("us-east-1", "babylog.media.oss.region", "babylog.media.oss-region"),
                first("babylog.media.oss.access-key", "babylog.media.oss.accessKey"),
                first("babylog.media.oss.secret-key", "babylog.media.oss.secretKey"),
                firstOr("babylog", "babylog.media.oss.bucket", "babylog.media.oss-bucket"),
                boolOr(false, "babylog.media.oss.path-style", "babylog.media.oss.pathStyle"));
    }

    private String first(String... keys) {
        for (String key : keys) {
            String value = env.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String firstOr(String fallback, String... keys) {
        String value = first(keys);
        return value == null ? fallback : value;
    }

    private boolean boolOr(boolean fallback, String... keys) {
        String value = first(keys);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private long longOr(long fallback, String... keys) {
        String value = first(keys);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stripSlashes(String v) {
        if (v == null || v.isBlank()) {
            return "babylog-media";
        }
        String out = v.trim();
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? "babylog-media" : out;
    }
}
