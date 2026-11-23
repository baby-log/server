package com.babylog.biz.media.vo;

import lombok.Data;

/**
 * 手动迁移工具结果（{@code 24} ③-3：存量对象搬迁只能显式手动触发）。
 */
@Data
public class MediaMigrateVO {

    private String fromBackend;
    private String toBackend;
    /** 扫描到的候选行数 */
    private int scanned;
    /** 成功搬迁并回写 storage_backend/object_key 的行数 */
    private int migrated;
    /** 中途失败的行数（行保持原后端与对象键，可重试） */
    private int failed;
    /** 已是目标后端而跳过的行数 */
    private int skipped;
}
