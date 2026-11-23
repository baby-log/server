package com.babylog.biz.media.vo;

import lombok.Data;

/**
 * 媒体文件元数据 VO（App 端点出站）。
 *
 * <p><b>全局契约坑</b>：所有 Long 字段由 {@code starter-core} 的 fastjson2
 * {@code WriteLongAsString} 自动序列化为字符串（id / childId / sizeBytes / 时间戳无一例外），
 * 本 VO 沿用项目惯例直接声明 Long，不重复手工转 String；{@code isFinalFrame} 是 TINYINT，
 * 用 Integer 0/1 输出，绝不发 true/false；null 键全局缺省（NON_NULL），四端容忍缺键。
 */
@Data
public class MediaFileVO {

    private Long id;
    private Long childId;
    private Long userId;
    private String storageBackend;
    private String objectKey;
    private Long sizeBytes;
    private String mimeType;
    private String linkedRecordType;
    private Long linkedRecordId;
    private Long detectedAt;
    private Long confirmedAt;
    /** 定稿帧标记：0/1（TINYINT 整数，非布尔） */
    private Integer isFinalFrame;
    private Long createdAt;
}
