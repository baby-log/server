package com.babylog.biz.media.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.biz.media.entity.BlMediaFile;
import com.babylog.biz.media.entity.BlMediaQuota;
import com.babylog.biz.media.mapper.BlMediaFileMapper;
import com.babylog.biz.media.mapper.BlMediaQuotaMapper;
import com.babylog.biz.media.vo.MediaQuotaVO;
import com.babylog.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 宝宝媒体容量配额（K2 / M69，契约 {@code 24} ②）。
 *
 * <p><b>used_bytes 口径</b>（24 ②-5 二选一，本批选定）：由 {@code bl_media_file} 实时聚合，
 * <b>不落冗余列</b>，避免「容量计数」与「文件行」双写漂移；读侧与淘汰任务共用
 * 本类 {@link #sumUsedBytes(long)} 同一个口径。
 *
 * <p>Mapper 保持零注解 SQL：聚合走类型化 Wrapper + {@code selectMaps}，
 * 显式带 {@code deleted = 0}（自定义 Wrapper 不依赖 {@code @TableLogic} 注入）。
 * 分页 / 取首行一律走 MP 分页对象或 {@code selectList}，不拼任何 SQL 片段。
 *
 * <p>{@code initialBytes} 展示初始容量、后台不改写；{@code adjustedBytes} 为后台动态调整值，
 * NULL = 未调整，生效容量回落初始容量。
 */
@Service
public class MediaQuotaService {

    private final BlMediaQuotaMapper quotaMapper;
    private final BlMediaFileMapper fileMapper;
    private final MediaStorageProperties properties;

    public MediaQuotaService(BlMediaQuotaMapper quotaMapper,
                             BlMediaFileMapper fileMapper,
                             MediaStorageProperties properties) {
        this.quotaMapper = quotaMapper;
        this.fileMapper = fileMapper;
        this.properties = properties;
    }

    /** 读侧配额 VO（无行则按默认初始容量惰性建行） */
    public MediaQuotaVO quota(long childId, long ownerUserId) {
        BlMediaQuota row = ensureQuota(childId, ownerUserId);
        return toVO(row, sumUsedBytes(childId));
    }

    /**
     * 后台动态调整（{@code adjustedBytes >= 0}）。
     *
     * <p>已存在的配额行<b>保留原 user_id</b>，不因管理员操作改写归属；仅「行尚不存在」时
     * 用 {@code ownerUserId} 惰性建行。读侧展示「初始容量」不受本方法影响。
     */
    public MediaQuotaVO adjust(long childId, long ownerUserId, Long adjustedBytes) {
        if (adjustedBytes == null || adjustedBytes < 0) {
            throw new ServiceException(BabyLogErrorCode.MEDIA_QUOTA_INVALID);
        }
        BlMediaQuota row = ensureQuota(childId, ownerUserId);
        row.setAdjustedBytes(adjustedBytes);
        quotaMapper.updateById(row);
        return toVO(row, sumUsedBytes(childId));
    }

    /** 生效容量 = adjustedBytes != null ? adjustedBytes : initialBytes */
    public long effectiveBytes(BlMediaQuota row) {
        return row.getAdjustedBytes() != null ? row.getAdjustedBytes() : row.getInitialBytes();
    }

    /** 全部配额行（容量淘汰任务逐宝宝遍历用；{@code @TableLogic} 自动滤 deleted=1） */
    public List<BlMediaQuota> allQuotas() {
        return quotaMapper.selectList(new LambdaQueryWrapper<>());
    }

    /**
     * 已用容量：{@code SUM(size_bytes) WHERE child_id=? AND deleted=0}。
     *
     * <p>返回 Map 的列名在不同驱动下大小写可能不同，故先取 {@code used_bytes}，
     * 取不到再扫第一个非空值；无行 / 驱动返回 null 一律按 0 处理。
     */
    public long sumUsedBytes(long childId) {
        QueryWrapper<BlMediaFile> query = new QueryWrapper<>();
        query.select("IFNULL(SUM(size_bytes), 0) AS used_bytes");
        query.eq("child_id", childId).eq("deleted", 0);
        List<Map<String, Object>> rows = fileMapper.selectMaps(query);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return 0L;
        }
        Map<String, Object> first = rows.get(0);
        Object value = first.get("used_bytes");
        if (value == null) {
            for (Map.Entry<String, Object> entry : first.entrySet()) {
                if (entry.getValue() != null) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 惰性建行（{@code uk_child} 唯一）。并发首传同一宝宝时允许唯一键冲突回查，
     * 不抛 5xx；DDL 有 {@code UNIQUE KEY uk_child}，冲突只会是「别人刚建好」。
     */
    public BlMediaQuota ensureQuota(long childId, long ownerUserId) {
        List<BlMediaQuota> existing = quotaMapper.selectList(new LambdaQueryWrapper<BlMediaQuota>()
                .eq(BlMediaQuota::getChildId, childId));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        BlMediaQuota row = new BlMediaQuota();
        row.setChildId(childId);
        row.setUserId(ownerUserId);
        row.setInitialBytes(properties.defaultQuotaBytes());
        row.setCreatedAt(System.currentTimeMillis());
        try {
            quotaMapper.insert(row);
            return row;
        } catch (DuplicateKeyException e) {
            List<BlMediaQuota> again = quotaMapper.selectList(new LambdaQueryWrapper<BlMediaQuota>()
                    .eq(BlMediaQuota::getChildId, childId));
            if (again.isEmpty()) {
                throw e;
            }
            return again.get(0);
        }
    }

    private MediaQuotaVO toVO(BlMediaQuota row, long used) {
        MediaQuotaVO vo = new MediaQuotaVO();
        vo.setId(row.getId());
        vo.setChildId(row.getChildId());
        vo.setInitialBytes(row.getInitialBytes());
        vo.setAdjustedBytes(row.getAdjustedBytes());
        long effective = effectiveBytes(row);
        vo.setEffectiveBytes(effective);
        vo.setUsedBytes(used);
        vo.setRemainingBytes(effective - used);
        return vo;
    }
}
