package com.babylog.biz.media.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.biz.media.entity.BlMediaFile;
import com.babylog.biz.media.mapper.BlMediaFileMapper;
import com.babylog.biz.media.storage.MediaStorageAdapter;
import com.babylog.biz.media.storage.MediaStorageRouter;
import com.babylog.biz.media.vo.MediaMigrateVO;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 手动迁移工具（K2 / M69，契约 {@code 24} ③-3）。
 *
 * <p><b>禁止隐式全量重定位</b>：改 {@code defaultBackend} / {@code keyPrefix} 只影响新写入；
 * 存量对象行自带 {@code storage_backend} + {@code object_key}，按行路由永不变。
 * 要让存量也用上新后端 / 新路径，只能由运维显式调用本工具（离线批处理，非运行时自动搬迁）。
 *
 * <p>单行迁移顺序：源对象存在性检查 → 读字节 → 按新前缀生成新键并写目标 →
 * 回写 DB 行的后端与新键 → best-effort 删源对象。
 * 关键：<b>DB 回写成功才切换行路由</b>，前面任一步失败都保持原行不变（可安全重试）；
 * 只有「删源对象」失败不回滚（目标已可用，源成孤儿，由运维清理，计入日志）。
 *
 * <p>COS → OSS 只是换 endpoint / 凭据 / bucket（同一 S3 兼容适配器），接口与业务层零改动。
 */
@Service
public class MediaMigrationService {

    private static final Logger log = LoggerFactory.getLogger(MediaMigrationService.class);

    private final BlMediaFileMapper fileMapper;
    private final MediaStorageRouter router;
    private final MediaStorageProperties properties;

    public MediaMigrationService(BlMediaFileMapper fileMapper,
                                 MediaStorageRouter router,
                                 MediaStorageProperties properties) {
        this.fileMapper = fileMapper;
        this.router = router;
        this.properties = properties;
    }

    /**
     * 把 {@code fromBackend} 的存量行搬到 {@code toBackend}，单批上限
     * {@code min(limit, batchSize)}；返回逐行统计。
     *
     * @throws ServiceException 8427（源目标相同）/ 8422（后端标识未注册）
     */
    public MediaMigrateVO migrate(String from, String to, int limit) {
        if (from == null || to == null || from.isBlank() || to.isBlank() || from.equals(to)) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_MIGRATE_FAILED);
        }
        MediaStorageAdapter source = router.adapterFor(from.trim());
        MediaStorageAdapter target = router.adapterFor(to.trim());
        int cap = limit <= 0 ? Math.max(1, properties.batchSize())
                : Math.min(limit, Math.max(1, properties.batchSize()));

        MediaMigrateVO vo = new MediaMigrateVO();
        vo.setFromBackend(from);
        vo.setToBackend(to);

        Page<BlMediaFile> page = fileMapper.selectPage(new Page<>(1, cap), new LambdaQueryWrapper<BlMediaFile>()
                .eq(BlMediaFile::getStorageBackend, from.trim())
                .orderByAsc(BlMediaFile::getCreatedAt));
        List<BlMediaFile> rows = page == null || page.getRecords() == null ? List.of() : page.getRecords();
        vo.setScanned(rows.size());

        for (BlMediaFile row : rows) {
            String oldKey = row.getObjectKey();
            try {
                if (!source.exists(oldKey)) {
                    // 源对象已不在（历史残留行）：跳过，不制造空目标对象
                    vo.setSkipped(vo.getSkipped() + 1);
                    continue;
                }
                byte[] data = source.get(oldKey);
                String newKey = router.newObjectKey(row.getChildId(), extensionOf(oldKey));
                target.put(newKey, data, row.getMimeType());
                row.setStorageBackend(to.trim());
                row.setObjectKey(newKey);
                fileMapper.updateById(row);
                try {
                    source.delete(oldKey);
                } catch (RuntimeException e) {
                    // 目标已可用、行已切路由；源对象成孤儿只登记，不回滚（避免把行切成不可用状态）
                    log.warn("[BabyLog][media] 迁移源对象删除失败（孤儿待清理）id={} oldKey={}", row.getId(), oldKey);
                }
                vo.setMigrated(vo.getMigrated() + 1);
            } catch (RuntimeException e) {
                log.warn("[BabyLog][media] 迁移单行失败，行保持原后端与键可重试 id={} key={}", row.getId(), oldKey);
                vo.setFailed(vo.getFailed() + 1);
            }
        }
        return vo;
    }

    private static String extensionOf(String objectKey) {
        if (objectKey == null) {
            return "bin";
        }
        int dot = objectKey.lastIndexOf('.');
        return dot < 0 || dot == objectKey.length() - 1 ? "bin" : objectKey.substring(dot + 1);
    }
}
