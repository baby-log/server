package com.babylog.biz.media.storage;

/**
 * 三后端统一存储适配器接口（K2 / M69，契约 {@code 24} ③）。
 *
 * <p>业务层只依赖本接口，不感知后端：{@code local} / {@code cos} / {@code oss}
 * 三个实现，COS 与 OSS 共用同一个 S3 兼容实现类，只换 endpoint / 凭据 / bucket。
 *
 * <p>quota 不在这里：容量配额由 {@code bl_media_quota} 承载（24 ②），是业务语义而非存储能力。
 *
 * <p>「路径可变更」由「每行落 {@code storage_backend}+{@code object_key}，按行路由」实现：
 * 配置变更只影响新写入，存量对象键永不变，旧对象迁移走显式手动工具。
 */
public interface MediaStorageAdapter {

    /** 后端标识，与 {@code bl_media_file.storage_backend} 逐字对应 */
    String backend();

    /** 对象是否存在（用于下载 / 删除前的存在性判定） */
    boolean exists(String objectKey);

    /** 写入对象（覆盖语义）；objectKey 由调用方生成并固化到行 */
    void put(String objectKey, byte[] data, String mimeType);

    /** 读取对象字节（V3 前仅小附件，不做流式分片） */
    byte[] get(String objectKey);

    /** 物理删除对象；必须幂等（S3 DeleteObject 对不存在对象返回成功），失败可重试 */
    void delete(String objectKey);
}
