package com.babylog.biz.media.config;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.media.storage.LocalMediaStorageAdapter;
import com.babylog.biz.media.storage.MediaStorageAdapter;
import com.babylog.biz.media.storage.MediaStorageRouter;
import com.babylog.biz.media.storage.S3MediaStorageAdapter;
import com.babylog.common.core.exception.ServiceException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 三后端装配（K2 / M69，契约 {@code 24} ③）。
 *
 * <p>业务层只认 {@link MediaStorageAdapter} 接口 + {@link MediaStorageRouter}：
 * 三个 bean 注入 List 后按 {@code backend()} 建索引，新增后端只加一个 bean、业务层零改动。
 *
 * <p>所有 bean 无条件装配：S3 侧配置不全时 client 为 null、调用返回 8421，
 * 但<b>不阻断 dev 起服</b>——本地后端始终可用，满足「至少本地 + COS 两个在 dev 实测」的
 * 本地侧零配置要求。
 */
@Configuration(proxyBeanMethods = false)
public class MediaStorageConfiguration {

    @Bean
    public LocalMediaStorageAdapter mediaLocalAdapter(MediaStorageProperties properties) {
        return new LocalMediaStorageAdapter(properties.localDir());
    }

    @Bean
    public S3MediaStorageAdapter mediaCosAdapter(MediaStorageProperties properties) {
        return new S3MediaStorageAdapter(properties.cosConfig());
    }

    @Bean
    public S3MediaStorageAdapter mediaOssAdapter(MediaStorageProperties properties) {
        return new S3MediaStorageAdapter(properties.ossConfig());
    }

    @Bean
    public MediaStorageRouter mediaStorageRouter(List<MediaStorageAdapter> adapters,
                                                 MediaStorageProperties properties) {
        return new MediaStorageRouter(adapters, properties);
    }

    /** 供其它组件复用的后端可用性断言，避免把 8421 判定散落各处 */
    public static ServiceException backendUnavailable() {
        return new ServiceException(BabyLogErrorCode.MEDIA_BACKEND_UNAVAILABLE);
    }
}
