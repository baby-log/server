package com.babylog.biz.media;

import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.biz.media.entity.BlMediaFile;
import com.babylog.biz.media.entity.BlMediaQuota;
import com.babylog.biz.media.mapper.BlMediaFileMapper;
import com.babylog.biz.media.service.MediaQuotaService;
import com.babylog.biz.media.service.MediaRetentionService;
import com.babylog.biz.media.storage.MediaStorageAdapter;
import com.babylog.biz.media.storage.MediaStorageRouter;
import com.babylog.biz.media.vo.MediaRetentionVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 淘汰契约单测（K2 / M69，契约 {@code 24} ④）：
 * 三类触发 / 已关联记录与定稿帧保护 / 两步删顺序 / 物理删失败保留 DB 行可重试 / 容量删最早。
 *
 * <p>不连库、不起 Spring：Mapper 与适配器全部 Mockito mock，只验证编排逻辑。
 */
@ExtendWith(MockitoExtension.class)
class MediaRetentionServiceTest {

    @Mock
    private BlMediaFileMapper fileMapper;
    @Mock
    private MediaQuotaService quotaService;
    @Mock
    private MediaStorageRouter router;
    @Mock
    private MediaStorageProperties properties;
    @Mock
    private MediaStorageAdapter adapter;

    private MediaRetentionService service;

    @BeforeEach
    void setUp() {
        service = new MediaRetentionService(fileMapper, quotaService, router, properties);
        lenient().when(properties.confirmedRetentionHours()).thenReturn(2L);
        lenient().when(properties.unconfirmedRetentionDays()).thenReturn(3L);
        lenient().when(properties.batchSize()).thenReturn(200);
        // 时间淘汰用例不进入容量分支；容量用例单独直接调 evictForCapacity
        lenient().when(quotaService.allQuotas()).thenReturn(List.of());
        lenient().when(router.adapterFor(anyString())).thenReturn(adapter);
    }

    @Test
    void timeBasedEvictionDeletesBothSidesAndCounts() {
        doReturn(pageOf(List.of(row(1L, 10L))), pageOf(List.of(row(2L, 20L))))
                .when(fileMapper).selectPage(ArgumentMatchers.<Page<BlMediaFile>>any(), any());

        MediaRetentionVO vo = service.run();

        assertThat(vo.getConfirmedExpired()).isEqualTo(1);
        assertThat(vo.getUnconfirmedExpired()).isEqualTo(1);
        assertThat(vo.getTotalDeleted()).isEqualTo(2);
        assertThat(vo.getPhysicalDeleteFailed()).isZero();
        // 物理删 + DB 逻辑删两步都做，且各两次
        verify(adapter, times(2)).delete(anyString());
        verify(fileMapper, times(2)).deleteById(anyLong());
    }

    @Test
    void linkedRecordAndFinalFrameRowsAreProtected() {
        BlMediaFile linked = row(1L, 10L);
        linked.setLinkedRecordType("VISIT");
        linked.setLinkedRecordId(999L);
        BlMediaFile finalFrame = row(2L, 20L);
        finalFrame.setIsFinalFrame(1);
        doReturn(pageOf(List.of(linked, finalFrame)), pageOf(List.<BlMediaFile>of()))
                .when(fileMapper).selectPage(ArgumentMatchers.<Page<BlMediaFile>>any(), any());

        MediaRetentionVO vo = service.run();

        assertThat(vo.getSkippedLinked()).isEqualTo(2);
        assertThat(vo.getTotalDeleted()).isZero();
        verify(adapter, never()).delete(anyString());
        verify(fileMapper, never()).deleteById(anyLong());
    }

    @Test
    void physicalDeleteFailureKeepsDbRowForNextRetry() {
        doReturn(pageOf(List.of(row(1L, 10L))), pageOf(List.<BlMediaFile>of()))
                .when(fileMapper).selectPage(ArgumentMatchers.<Page<BlMediaFile>>any(), any());
        doThrow(new RuntimeException("io failed")).when(adapter).delete(anyString());

        MediaRetentionVO vo = service.run();

        assertThat(vo.getPhysicalDeleteFailed()).isEqualTo(1);
        assertThat(vo.getTotalDeleted()).isZero();
        // 关键：物理删失败 ⇒ 绝不逻辑删，行保留 deleted=0 供下次任务自然重试
        verify(fileMapper, never()).deleteById(anyLong());
    }

    @Test
    void capacityEvictsOldestFirstAndStopsWhenEnoughFreed() {
        BlMediaFile oldest = row(1L, 100L);
        oldest.setCreatedAt(100L);
        BlMediaFile second = row(2L, 100L);
        second.setCreatedAt(200L);
        BlMediaFile newest = row(3L, 100L);
        newest.setCreatedAt(300L);
        when(fileMapper.selectList(any())).thenReturn(List.of(oldest, second, newest));

        MediaRetentionVO vo = new MediaRetentionVO();
        service.evictForCapacity(42L, 1000L, 150L, vo);

        assertThat(vo.getCapacityEvicted()).isEqualTo(2);
        verify(fileMapper).deleteById(1L);
        verify(fileMapper).deleteById(2L);
        verify(fileMapper, never()).deleteById(3L);
    }

    @Test
    void capacityBranchRunsPerQuotaWhenUsedExceedsEffective() {
        BlMediaQuota quota = new BlMediaQuota();
        quota.setChildId(42L);
        quota.setUserId(7L);
        quota.setInitialBytes(100L);
        when(quotaService.allQuotas()).thenReturn(List.of(quota));
        when(quotaService.effectiveBytes(quota)).thenReturn(100L);
        when(quotaService.sumUsedBytes(42L)).thenReturn(180L);
        doReturn(pageOf(List.<BlMediaFile>of()), pageOf(List.<BlMediaFile>of()))
                .when(fileMapper).selectPage(ArgumentMatchers.<Page<BlMediaFile>>any(), any());
        BlMediaFile oldest = row(9L, 80L);
        oldest.setCreatedAt(100L);
        when(fileMapper.selectList(any())).thenReturn(List.of(oldest));

        MediaRetentionVO vo = service.run();

        // 超额 80 字节，删最早一行即腾够
        assertThat(vo.getCapacityEvicted()).isEqualTo(1);
        assertThat(vo.getTotalDeleted()).isEqualTo(1);
        verify(fileMapper).deleteById(9L);
        verify(quotaService).sumUsedBytes(eq(42L));
    }

    private static BlMediaFile row(long id, long size) {
        BlMediaFile row = new BlMediaFile();
        row.setId(id);
        row.setChildId(42L);
        row.setUserId(7L);
        row.setStorageBackend("local");
        row.setObjectKey("key-" + id);
        row.setSizeBytes(size);
        row.setIsFinalFrame(0);
        row.setDetectedAt(1000L + id);
        row.setCreatedAt(1000L + id);
        return row;
    }

    private static Page<BlMediaFile> pageOf(List<BlMediaFile> rows) {
        Page<BlMediaFile> page = new Page<>(1, 200);
        page.setRecords(rows);
        page.setTotal(rows.size());
        return page;
    }
}
