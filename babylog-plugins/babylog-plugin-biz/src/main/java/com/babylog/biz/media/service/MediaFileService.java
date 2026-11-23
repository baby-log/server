package com.babylog.biz.media.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.biz.media.dto.MediaConfirmCmd;
import com.babylog.biz.media.dto.MediaUploadCmd;
import com.babylog.biz.media.entity.BlMediaFile;
import com.babylog.biz.media.entity.BlMediaQuota;
import com.babylog.biz.media.mapper.BlMediaFileMapper;
import com.babylog.biz.media.storage.MediaStorageAdapter;
import com.babylog.biz.media.storage.MediaStorageRouter;
import com.babylog.biz.media.vo.MediaDownload;
import com.babylog.biz.media.vo.MediaFileVO;
import com.babylog.biz.media.vo.MediaRetentionVO;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 媒体文件业务端点服务（K2 / M69，契约 {@code 24} ② ③ ④）。
 *
 * <p><b>读侧家庭口径</b>：所有按 id 的读 / 下载 / 删除都先取
 * {@code SyncAccessScopeResolver.resolveAccess(userId).visibleUserIds()}，
 * 行不在范围内与「不存在」返回同一个 8420（不区分，避免枚举探测）——与家庭七端点同款。
 *
 * <p><b>V3 前零画面数据</b>（24 ①-4）：本服务只承载<b>附件语义</b>（E6 就医附件 /
 * V3.1 手写导入拍照），{@code image/*} / {@code application/pdf} 等允许，
 * {@code video/*} 一律 8423 拒收；不接摄像头帧流、不做云同步开关（归 K8）。
 *
 * <p><b>user_id 盖章</b>：上传时由服务端按 token 写入，禁客户端传值；
 * {@code user_id} 是直写 {@code bl_*} 表的必填语义列（正服迁移漏写落 DEFAULT 0 拉不到的坑）。
 *
 * <p><b>按行路由</b>：读 / 下载 / 删除取行内 {@code storage_backend}，不取当前默认后端，
 * 保证「配置变更只对新写入生效」。
 */
@Service
public class MediaFileService {

    private final BlMediaFileMapper fileMapper;
    private final MediaQuotaService quotaService;
    private final MediaRetentionService retentionService;
    private final MediaStorageRouter router;
    private final MediaStorageProperties properties;
    private final SyncAccessScopeResolver scopeResolver;

    public MediaFileService(BlMediaFileMapper fileMapper,
                            MediaQuotaService quotaService,
                            MediaRetentionService retentionService,
                            MediaStorageRouter router,
                            MediaStorageProperties properties,
                            SyncAccessScopeResolver scopeResolver) {
        this.fileMapper = fileMapper;
        this.quotaService = quotaService;
        this.retentionService = retentionService;
        this.router = router;
        this.properties = properties;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 上传：校验 → 配额（满则先「删最早」腾挪）→ 物理写 → 落 DB 行。
     *
     * <p>先物理写再落行：DB 插入失败时 best-effort 删回对象，避免留下无主文件。
     * 反之若先落行，物理写失败会留下「有行无对象」的坏数据。
     */
    public MediaFileVO upload(long userId, MediaUploadCmd cmd, byte[] data, String defaultMime) {
        if (cmd == null || cmd.getChildId() == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        if (data == null || data.length == 0 || data.length > properties.maxObjectBytes()) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_PAYLOAD_INVALID);
        }
        String mime = resolveMime(cmd, defaultMime);
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/")) {
            // 24 ①-4：V3 前一律禁止上传画面；视频帧流归 K9，本端点零画面
            throw new ServiceException(BabyLogErrorCode.MEDIA_PAYLOAD_INVALID);
        }

        long childId = cmd.getChildId();
        BlMediaQuota quota = quotaService.ensureQuota(childId, userId);
        long effective = quotaService.effectiveBytes(quota);
        long used = quotaService.sumUsedBytes(childId);
        if (used + data.length > effective) {
            // 24 ④-1 容量满「删最早」：先按 createdAt 升序腾出超额，再复核
            MediaRetentionVO stats = new MediaRetentionVO();
            retentionService.evictForCapacity(childId, effective, used + data.length - effective, stats);
            used = quotaService.sumUsedBytes(childId);
            if (used + data.length > effective) {
                // 保护文件（已关联 / 定稿帧）不参与淘汰 ⇒ 仍超配额则拒收，交后台调配额
                throw new ServiceException(BabyLogErrorCode.MEDIA_QUOTA_EXCEEDED);
            }
        }

        String backend = resolveBackend(cmd.getBackend());
        MediaStorageAdapter adapter = router.adapterFor(backend);
        String objectKey = router.newObjectKey(childId, extensionOf(mime));
        adapter.put(objectKey, data, mime);

        long now = System.currentTimeMillis();
        BlMediaFile row = new BlMediaFile();
        row.setChildId(childId);
        row.setUserId(userId);
        row.setStorageBackend(backend);
        row.setObjectKey(objectKey);
        row.setSizeBytes((long) data.length);
        row.setMimeType(mime);
        row.setLinkedRecordType(blankToNull(cmd.getLinkedRecordType()));
        row.setLinkedRecordId(cmd.getLinkedRecordId());
        row.setDetectedAt(cmd.getDetectedAt() != null ? cmd.getDetectedAt() : now);
        row.setConfirmedAt(null);
        row.setIsFinalFrame(normalize01(cmd.getIsFinalFrame()));
        row.setCreatedAt(now);
        try {
            fileMapper.insert(row);
        } catch (RuntimeException e) {
            // DB 落行失败 ⇒ 回滚已写对象（best-effort，失败只记日志，不覆盖原异常）
            try {
                adapter.delete(objectKey);
            } catch (RuntimeException rollbackFailure) {
                // 无主对象由后续运维清理；此处不干扰主异常上抛
            }
            throw e;
        }
        return toVO(row);
    }

    /** 元数据详情（家庭可见范围内） */
    public MediaFileVO get(long userId, long id) {
        return toVO(requireVisible(userId, id));
    }

    /** 按宝宝列媒体文件（家庭可见范围内，createdAt 降序） */
    public List<MediaFileVO> list(long userId, long childId) {
        Set<Long> scope = scopeOf(userId);
        if (scope.isEmpty()) {
            return List.of();
        }
        List<BlMediaFile> rows = fileMapper.selectList(new LambdaQueryWrapper<BlMediaFile>()
                .eq(BlMediaFile::getChildId, childId)
                .in(BlMediaFile::getUserId, scope)
                .orderByDesc(BlMediaFile::getCreatedAt));
        return rows.stream().map(this::toVO).toList();
    }

    /** 下载字节（按行内后端路由；controller 包成 ResponseEntity） */
    public MediaDownload download(long userId, long id) {
        BlMediaFile row = requireVisible(userId, id);
        byte[] data = router.adapterFor(row.getStorageBackend()).get(row.getObjectKey());
        return new MediaDownload(row.getObjectKey(), row.getMimeType(), data);
    }

    /**
     * 手动删除：与淘汰同款两步 —— 先物理删（幂等，失败抛 8426 且 DB 行保留可重试），
     * 成功后才 {@code deleteById} 逻辑删。已关联正式记录的文件也允许用户主动删除
     * （④-2 的「排除」只约束自动淘汰，不约束用户显式操作）。
     */
    public void delete(long userId, long id) {
        BlMediaFile row = requireVisible(userId, id);
        router.adapterFor(row.getStorageBackend()).delete(row.getObjectKey());
        fileMapper.deleteById(id);
    }

    /**
     * 确认 / 定稿帧转存（24 ④-4）：置 {@code confirmedAt} + {@code isFinalFrame=1} +
     * 关联正式记录。不复制字节、不新建行 ⇒ 该行从此被 {@code isProtected} 排除、长期保留。
     */
    public MediaFileVO confirm(long userId, long id, MediaConfirmCmd cmd) {
        if (cmd == null || blankToNull(cmd.getLinkedRecordType()) == null || cmd.getLinkedRecordId() == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        BlMediaFile row = requireVisible(userId, id);
        row.setLinkedRecordType(cmd.getLinkedRecordType().trim());
        row.setLinkedRecordId(cmd.getLinkedRecordId());
        row.setConfirmedAt(System.currentTimeMillis());
        row.setIsFinalFrame(1);
        fileMapper.updateById(row);
        return toVO(row);
    }

    // ---------- 内部 ----------

    private BlMediaFile requireVisible(long userId, long id) {
        BlMediaFile row = fileMapper.selectById(id);
        if (row == null || !scopeOf(userId).contains(row.getUserId())) {
            // 不存在与无权访问不区分，避免枚举探测
            throw new ServiceException(BabyLogErrorCode.MEDIA_FILE_NOT_FOUND);
        }
        return row;
    }

    private Set<Long> scopeOf(long userId) {
        List<Long> visible = scopeResolver.resolveAccess(userId).visibleUserIds();
        return visible == null ? Set.of() : new HashSet<>(visible);
    }

    /**
     * 生产只走 {@link MediaStorageRouter#writeBackend()}；{@code cmd.backend} 仅 dev 实测用，
     * 允许显式指定已注册后端以便「本地 + COS 两个在 dev 实测」，非法值由 adapterFor 抛 8422。
     */
    private String resolveBackend(String override) {
        if (override == null || override.isBlank()) {
            return router.writeBackend();
        }
        String backend = override.trim().toLowerCase(Locale.ROOT);
        router.adapterFor(backend);
        return backend;
    }

    private static String resolveMime(MediaUploadCmd cmd, String fallback) {
        String explicit = blankToNull(cmd.getMimeType());
        return explicit != null ? explicit : blankToNull(fallback);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int normalize01(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    /** MIME → 对象键后缀（只取字母数字，交给 Router 再 sanitize 一次） */
    private static String extensionOf(String mime) {
        if (mime == null) {
            return "bin";
        }
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "image/heic", "image/heif" -> "heic";
            case "application/pdf" -> "pdf";
            default -> {
                String sub = mime.contains("/") ? mime.substring(mime.indexOf('/') + 1) : mime;
                String clean = sub.replaceAll("[^a-z0-9]", "");
                yield clean.isEmpty() ? "bin" : clean;
            }
        };
    }

    private MediaFileVO toVO(BlMediaFile row) {
        MediaFileVO vo = new MediaFileVO();
        vo.setId(row.getId());
        vo.setChildId(row.getChildId());
        vo.setUserId(row.getUserId());
        vo.setStorageBackend(row.getStorageBackend());
        vo.setObjectKey(row.getObjectKey());
        vo.setSizeBytes(row.getSizeBytes());
        vo.setMimeType(row.getMimeType());
        vo.setLinkedRecordType(row.getLinkedRecordType());
        vo.setLinkedRecordId(row.getLinkedRecordId());
        vo.setDetectedAt(row.getDetectedAt());
        vo.setConfirmedAt(row.getConfirmedAt());
        vo.setIsFinalFrame(row.getIsFinalFrame());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }
}
