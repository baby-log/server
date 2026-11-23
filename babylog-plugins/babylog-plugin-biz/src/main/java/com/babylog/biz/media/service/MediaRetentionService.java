package com.babylog.biz.media.service;

import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.biz.media.entity.BlMediaFile;
import com.babylog.biz.media.entity.BlMediaQuota;
import com.babylog.biz.media.mapper.BlMediaFileMapper;
import com.babylog.biz.media.storage.MediaStorageAdapter;
import com.babylog.biz.media.storage.MediaStorageRouter;
import com.babylog.biz.media.vo.MediaRetentionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 媒体淘汰（K2 / M69，契约 {@code 24} ④）。
 *
 * <p>三类触发：已确认超 {@code 2h} / 未确认超 {@code 3 天} / 容量满「删最早」
 * （按 {@code createdAt} 升序，允许误差 —— 本质是定时任务 + LRU，非精确定时）。
 *
 * <p><b>保护规则统一在 {@link #isProtected(BlMediaFile)}</b>（不在 SQL 里各写一份，避免口径漂移）：
 * ① {@code linkedRecordType} + {@code linkedRecordId} 同时非空 = 已关联正式记录（④-2）；
 * ② {@code isFinalFrame=1} 定稿帧（④-4，确认瞬间转存记录附件、长期保留）。
 * 时间淘汰与容量淘汰都跳过这两类，并计入 {@code skippedLinked}。
 *
 * <p><b>两步删与顺序（④-3 授权「顺序与失败重试口径归 K2」）</b>：本批选
 * <b>先物理删、成功后才逻辑删</b>：
 * <ul>
 *   <li>适配器 {@code delete} 幂等（S3 DeleteObject / 本地 deleteIfExists），失败可安全重试；</li>
 *   <li>物理删失败 ⇒ 保留 {@code deleted=0} 的 DB 行，下次任务自然重试，不产生孤儿；</li>
 *   <li>若反过来「先逻辑删」，{@code @TableLogic} 会让 {@code deleted=1} 行对常规查询不可见，
 *       物理删一旦失败就留下「DB 已删、对象还在」且<b>扫不回来</b>的孤儿。</li>
 * </ul>
 * 两步都做，严格满足「禁止只删 DB 不删文件、也禁止只删文件不删 DB」。
 *
 * <p>本类只提供 {@link #run()} 与 {@link #evictForCapacity}，<b>不含任何调度注册</b>：
 * xxl-job 执行器 / 任务启停交龙哥，AI 不启 xxl-job。
 */
@Service
public class MediaRetentionService {

    private static final Logger log = LoggerFactory.getLogger(MediaRetentionService.class);

    private final BlMediaFileMapper fileMapper;
    private final MediaQuotaService quotaService;
    private final MediaStorageRouter router;
    private final MediaStorageProperties properties;

    public MediaRetentionService(BlMediaFileMapper fileMapper,
                                 MediaQuotaService quotaService,
                                 MediaStorageRouter router,
                                 MediaStorageProperties properties) {
        this.fileMapper = fileMapper;
        this.quotaService = quotaService;
        this.router = router;
        this.properties = properties;
    }

    /** 一次完整淘汰（xxl-job / 运维诊断端点共用同一实现） */
    public MediaRetentionVO run() {
        long now = System.currentTimeMillis();
        long confirmedCutoff = now - properties.confirmedRetentionHours() * 3_600_000L;
        long unconfirmedCutoff = now - properties.unconfirmedRetentionDays() * 86_400_000L;
        int batch = Math.max(1, properties.batchSize());
        MediaRetentionVO vo = new MediaRetentionVO();

        // ① 已确认超 2h（confirmedAt 非空且早于切点）
        List<BlMediaFile> confirmed = page(new LambdaQueryWrapper<BlMediaFile>()
                .isNotNull(BlMediaFile::getConfirmedAt)
                .lt(BlMediaFile::getConfirmedAt, confirmedCutoff), batch);
        for (BlMediaFile row : confirmed) {
            if (isProtected(row)) {
                vo.setSkippedLinked(vo.getSkippedLinked() + 1);
                continue;
            }
            if (evict(row, vo)) {
                vo.setConfirmedExpired(vo.getConfirmedExpired() + 1);
            }
        }

        // ② 未确认超 3 天（confirmedAt 为空，以 detectedAt 为基准）
        List<BlMediaFile> unconfirmed = page(new LambdaQueryWrapper<BlMediaFile>()
                .isNull(BlMediaFile::getConfirmedAt)
                .isNotNull(BlMediaFile::getDetectedAt)
                .lt(BlMediaFile::getDetectedAt, unconfirmedCutoff), batch);
        for (BlMediaFile row : unconfirmed) {
            if (isProtected(row)) {
                vo.setSkippedLinked(vo.getSkippedLinked() + 1);
                continue;
            }
            if (evict(row, vo)) {
                vo.setUnconfirmedExpired(vo.getUnconfirmedExpired() + 1);
            }
        }

        // ③ 容量满「删最早」：逐宝宝，used > effective 时按 createdAt 升序腾出超额部分
        for (BlMediaQuota quota : quotaService.allQuotas()) {
            long effective = quotaService.effectiveBytes(quota);
            long used = quotaService.sumUsedBytes(quota.getChildId());
            if (used > effective) {
                evictForCapacity(quota.getChildId(), effective, used - effective, vo);
            }
        }

        vo.setTotalDeleted(vo.getConfirmedExpired() + vo.getUnconfirmedExpired() + vo.getCapacityEvicted());
        log.info("[BabyLog][media] 淘汰完成 confirmed={} unconfirmed={} capacity={} skippedLinked={} "
                        + "physicalDeleteFailed={} total={}",
                vo.getConfirmedExpired(), vo.getUnconfirmedExpired(), vo.getCapacityEvicted(),
                vo.getSkippedLinked(), vo.getPhysicalDeleteFailed(), vo.getTotalDeleted());
        return vo;
    }

    /**
     * 容量腾挪（上传前「配额已满」与任务 ③ 共用）：按 {@code createdAt} 升序删最早的非保护文件，
     * 直到腾出 {@code bytesToFree} 或候选耗尽。统计累加进调用方的 {@code MediaRetentionVO}。
     */
    public void evictForCapacity(long childId, long effectiveBytes, long bytesToFree, MediaRetentionVO vo) {
        if (bytesToFree <= 0) {
            return;
        }
        List<BlMediaFile> candidates = fileMapper.selectList(new LambdaQueryWrapper<BlMediaFile>()
                .eq(BlMediaFile::getChildId, childId)
                .orderByAsc(BlMediaFile::getCreatedAt));
        long freed = 0;
        for (BlMediaFile row : candidates) {
            if (freed >= bytesToFree) {
                break;
            }
            if (isProtected(row)) {
                vo.setSkippedLinked(vo.getSkippedLinked() + 1);
                continue;
            }
            if (evict(row, vo)) {
                freed += row.getSizeBytes() == null ? 0L : row.getSizeBytes();
                vo.setCapacityEvicted(vo.getCapacityEvicted() + 1);
            }
        }
    }

    /** 已关联正式记录 / 定稿帧：所有淘汰路径一律跳过（长期保留） */
    private static boolean isProtected(BlMediaFile row) {
        boolean linked = row.getLinkedRecordType() != null && !row.getLinkedRecordType().isBlank()
                && row.getLinkedRecordId() != null;
        boolean finalFrame = Integer.valueOf(1).equals(row.getIsFinalFrame());
        return linked || finalFrame;
    }

    /**
     * 单文件两步删：先物理删（失败计 {@code physicalDeleteFailed}、保留 DB 行待重试），
     * 成功后才 {@code deleteById} 逻辑删（{@code @TableLogic} ⇒ UPDATE deleted=1，
     * 由 {@code server_updated_at ON UPDATE} 自动刷新游标，不手写 version+1）。
     */
    private boolean evict(BlMediaFile row, MediaRetentionVO vo) {
        MediaStorageAdapter adapter;
        try {
            adapter = router.adapterFor(row.getStorageBackend());
            adapter.delete(row.getObjectKey());
        } catch (RuntimeException e) {
            log.warn("[BabyLog][media] 物理删失败，保留 DB 行待下次重试 id={} backend={} key={}",
                    row.getId(), row.getStorageBackend(), row.getObjectKey());
            vo.setPhysicalDeleteFailed(vo.getPhysicalDeleteFailed() + 1);
            return false;
        }
        fileMapper.deleteById(row.getId());
        return true;
    }

    /** 单批取候选（MP 分页对象，避免一次全表扫描；不拼任何 SQL 片段） */
    private List<BlMediaFile> page(LambdaQueryWrapper<BlMediaFile> wrapper, int size) {
        Page<BlMediaFile> result = fileMapper.selectPage(new Page<>(1, size), wrapper);
        return result == null || result.getRecords() == null ? List.of() : result.getRecords();
    }
}
