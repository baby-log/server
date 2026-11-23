package com.babylog.biz.media.storage;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

/**
 * S3 兼容后端（COS / OSS 共用实现类，K2 / M69，契约 {@code 24} ③）。
 *
 * <p><b>复用既有能力、零新 SDK</b>：与 {@code babylog-plugin-file} 的
 * {@code S3CompatibleStorageStrategy} 用同一个 {@code software.amazon.awssdk:s3}
 * （BOM 已仲裁 2.49.3）、同一套 {@code babylog.file.*} 配置键、同一构造范式
 * （StaticCredentialsProvider + Region + pathStyle + endpointOverride）。
 * 差别只在：本类走<b>服务端字节流</b> put/get/delete/exists（媒体端点需要真实搬运字节、
 * 淘汰任务需要物理删），而 file 插件走预签名直传 —— 因此不能直接用它的
 * {@code IFileFacade}（file.api 契约无 delete / 无字节流方法），也不能跨插件依赖其实现层
 * （P0 ArchUnit C1 / A1-A2 禁止）。COS→OSS 只是换 endpoint / 凭据 / bucket，接口与业务层零改动。
 *
 * <p>未配置齐全时不建 client，所有操作返回 8421（不阻断 dev 起服）。
 */
public class S3MediaStorageAdapter implements MediaStorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(S3MediaStorageAdapter.class);

    private final MediaStorageProperties.S3Config config;
    private final S3Client client;

    public S3MediaStorageAdapter(MediaStorageProperties.S3Config config) {
        this.config = config;
        if (!config.configured()) {
            this.client = null;
            log.info("[BabyLog][media] S3 后端 {} 未配置完整，端点调用将返回 8421", config.backend());
            return;
        }
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(config.pathStyle())
                .build();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .build();
    }

    @Override
    public String backend() {
        return config.backend();
    }

    @Override
    public boolean exists(String objectKey) {
        S3Client c = requireClient();
        try {
            c.headObject(HeadObjectRequest.builder().bucket(config.bucket()).key(objectKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw ioFailure("head", objectKey, e);
        }
    }

    @Override
    public void put(String objectKey, byte[] data, String mimeType) {
        S3Client c = requireClient();
        try {
            PutObjectRequest.Builder builder = PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(objectKey);
            if (mimeType != null && !mimeType.isBlank()) {
                builder.contentType(mimeType);
            }
            c.putObject(builder.build(), RequestBody.fromBytes(data));
        } catch (RuntimeException e) {
            throw ioFailure("put", objectKey, e);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        S3Client c = requireClient();
        try {
            return c.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (RuntimeException e) {
            throw ioFailure("get", objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        S3Client c = requireClient();
        try {
            // S3 DeleteObject 对不存在对象幂等成功 ⇒ 淘汰任务失败可安全重试
            c.deleteObject(DeleteObjectRequest.builder().bucket(config.bucket()).key(objectKey).build());
        } catch (RuntimeException e) {
            throw ioFailure("delete", objectKey, e);
        }
    }

    private S3Client requireClient() {
        if (client == null) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_BACKEND_UNAVAILABLE);
        }
        return client;
    }

    private static ServiceException ioFailure(String op, String objectKey, RuntimeException cause) {
        log.error("[BabyLog][media] S3 {} 失败 objectKey={}", op, objectKey, cause);
        return new ServiceException(BabyLogErrorCode.MEDIA_STORAGE_IO_FAILED);
    }
}
