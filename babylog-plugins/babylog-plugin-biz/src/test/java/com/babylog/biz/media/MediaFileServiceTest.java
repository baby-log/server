package com.babylog.biz.media;

import com.babylog.biz.media.config.MediaStorageProperties;
import com.babylog.biz.media.dto.MediaConfirmCmd;
import com.babylog.biz.media.dto.MediaUploadCmd;
import com.babylog.biz.media.entity.BlMediaFile;
import com.babylog.biz.media.entity.BlMediaQuota;
import com.babylog.biz.media.mapper.BlMediaFileMapper;
import com.babylog.biz.media.service.MediaFileService;
import com.babylog.biz.media.service.MediaQuotaService;
import com.babylog.biz.media.service.MediaRetentionService;
import com.babylog.biz.media.storage.MediaStorageAdapter;
import com.babylog.biz.media.storage.MediaStorageRouter;
import com.babylog.biz.media.vo.MediaFileVO;
import com.babylog.biz.media.vo.MediaRetentionVO;
import com.babylog.biz.sync.support.SyncAccessScope;
import com.babylog.biz.sync.support.SyncAccessScopeResolver;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 媒体文件服务单测（K2 / M69）：user_id 盖章 / V3 前拒画面 / 配额满先删最早仍超则 8424 /
 * 家庭可见范围 / 两步删顺序 / 确认定稿帧转存。
 */
@ExtendWith(MockitoExtension.class)
class MediaFileServiceTest {

    @Mock
    private BlMediaFileMapper fileMapper;
    @Mock
    private MediaQuotaService quotaService;
    @Mock
    private MediaRetentionService retentionService;
    @Mock
    private MediaStorageRouter router;
    @Mock
    private MediaStorageProperties properties;
    @Mock
    private SyncAccessScopeResolver scopeResolver;
    @Mock
    private MediaStorageAdapter adapter;

    private MediaFileService service;

    @BeforeEach
    void setUp() {
        service = new MediaFileService(fileMapper, quotaService, retentionService,
                router, properties, scopeResolver);
    }

    @Test
    void uploadStampsUserIdAndUsesWriteBackend() {
        when(properties.maxObjectBytes()).thenReturn(1024L);
        BlMediaQuota quota = quota(10000L);
        when(quotaService.ensureQuota(100L, 7L)).thenReturn(quota);
        when(quotaService.effectiveBytes(quota)).thenReturn(10000L);
        when(quotaService.sumUsedBytes(100L)).thenReturn(0L);
        when(router.writeBackend()).thenReturn("local");
        when(router.adapterFor("local")).thenReturn(adapter);
        when(router.newObjectKey(eq(100L), anyString())).thenReturn("media/1.png");
        when(fileMapper.insert(any(BlMediaFile.class))).thenReturn(1);

        MediaUploadCmd cmd = new MediaUploadCmd();
        cmd.setChildId(100L);
        MediaFileVO vo = service.upload(7L, cmd, new byte[]{1, 2, 3}, "image/png");

        assertThat(vo.getUserId()).isEqualTo(7L);
        assertThat(vo.getChildId()).isEqualTo(100L);
        assertThat(vo.getStorageBackend()).isEqualTo("local");
        assertThat(vo.getObjectKey()).isEqualTo("media/1.png");
        assertThat(vo.getSizeBytes()).isEqualTo(3L);
        assertThat(vo.getMimeType()).isEqualTo("image/png");
        assertThat(vo.getIsFinalFrame()).isZero();
        assertThat(vo.getConfirmedAt()).isNull();
        assertThat(vo.getCreatedAt()).isNotNull();
        verify(adapter).put(eq("media/1.png"), any(byte[].class), eq("image/png"));
        verify(fileMapper).insert(any(BlMediaFile.class));
    }

    @Test
    void uploadRollsBackObjectWhenDbInsertFails() {
        when(properties.maxObjectBytes()).thenReturn(1024L);
        BlMediaQuota quota = quota(10000L);
        when(quotaService.ensureQuota(100L, 7L)).thenReturn(quota);
        when(quotaService.effectiveBytes(quota)).thenReturn(10000L);
        when(quotaService.sumUsedBytes(100L)).thenReturn(0L);
        when(router.writeBackend()).thenReturn("local");
        when(router.adapterFor("local")).thenReturn(adapter);
        when(router.newObjectKey(eq(100L), anyString())).thenReturn("media/1.png");
        when(fileMapper.insert(any(BlMediaFile.class))).thenThrow(new RuntimeException("db down"));

        MediaUploadCmd cmd = new MediaUploadCmd();
        cmd.setChildId(100L);

        assertThatThrownBy(() -> service.upload(7L, cmd, new byte[]{1}, "image/png"))
                .isInstanceOf(RuntimeException.class);
        verify(adapter).delete("media/1.png");
    }

    @Test
    void videoPayloadRejectedBeforeAnyWrite() {
        when(properties.maxObjectBytes()).thenReturn(1024L);
        MediaUploadCmd cmd = new MediaUploadCmd();
        cmd.setChildId(100L);
        cmd.setMimeType("video/mp4");

        assertThatThrownBy(() -> service.upload(7L, cmd, new byte[]{1}, "video/mp4"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8423));
        verifyNoInteractions(router, adapter, fileMapper, quotaService);
    }

    @Test
    void emptyPayloadRejected() {
        // 空体在 maxObjectBytes() 之前短路，故不 stub 它（Mockito 严格桩会把未用桩判为错误）
        MediaUploadCmd cmd = new MediaUploadCmd();
        cmd.setChildId(100L);

        assertThatThrownBy(() -> service.upload(7L, cmd, new byte[0], "image/png"))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8423));
        verifyNoInteractions(router, adapter, fileMapper, quotaService);
    }

    @Test
    void quotaExceededAfterCapacityEvictionStillFullThrows8424() {
        when(properties.maxObjectBytes()).thenReturn(1024L);
        BlMediaQuota quota = quota(100L);
        when(quotaService.ensureQuota(100L, 7L)).thenReturn(quota);
        when(quotaService.effectiveBytes(quota)).thenReturn(100L);
        // 淘汰前后都是 90 ⇒ 保护文件腾不出空间
        when(quotaService.sumUsedBytes(100L)).thenReturn(90L, 90L);
        MediaUploadCmd cmd = new MediaUploadCmd();
        cmd.setChildId(100L);

        assertThatThrownBy(() -> service.upload(7L, cmd, new byte[20], null))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8424));
        verify(retentionService).evictForCapacity(eq(100L), eq(100L), eq(10L), any(MediaRetentionVO.class));
        verifyNoInteractions(router, adapter);
    }

    @Test
    void uploadHonoursBackendOverrideForDevBackendSmoke() {
        when(properties.maxObjectBytes()).thenReturn(1024L);
        BlMediaQuota quota = quota(10000L);
        when(quotaService.ensureQuota(100L, 7L)).thenReturn(quota);
        when(quotaService.effectiveBytes(quota)).thenReturn(10000L);
        when(quotaService.sumUsedBytes(100L)).thenReturn(0L);
        when(router.adapterFor("cos")).thenReturn(adapter);
        when(router.newObjectKey(eq(100L), anyString())).thenReturn("media/cos-1.png");
        when(fileMapper.insert(any(BlMediaFile.class))).thenReturn(1);
        MediaUploadCmd cmd = new MediaUploadCmd();
        cmd.setChildId(100L);
        cmd.setBackend("cos");

        MediaFileVO vo = service.upload(7L, cmd, new byte[]{1}, "image/png");

        assertThat(vo.getStorageBackend()).isEqualTo("cos");
        verify(adapter).put(eq("media/cos-1.png"), any(byte[].class), eq("image/png"));
    }

    @Test
    void outsideFamilyScopeReturns8420() {
        BlMediaFile row = visibleRow(999L);
        when(fileMapper.selectById(5L)).thenReturn(row);
        when(scopeResolver.resolveAccess(7L)).thenReturn(SyncAccessScope.singleUser(7L));

        assertThatThrownBy(() -> service.get(7L, 5L))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(8420));
        verifyNoInteractions(router, adapter);
    }

    @Test
    void deleteDoesPhysicalDeleteBeforeLogicalDelete() {
        when(fileMapper.selectById(5L)).thenReturn(visibleRow(7L));
        when(scopeResolver.resolveAccess(7L)).thenReturn(SyncAccessScope.singleUser(7L));
        when(router.adapterFor("local")).thenReturn(adapter);

        service.delete(7L, 5L);

        InOrder order = inOrder(adapter, fileMapper);
        order.verify(adapter).delete("key-5");
        order.verify(fileMapper).deleteById(5L);
    }

    @Test
    void deleteKeepsDbRowWhenPhysicalDeleteFails() {
        when(fileMapper.selectById(5L)).thenReturn(visibleRow(7L));
        when(scopeResolver.resolveAccess(7L)).thenReturn(SyncAccessScope.singleUser(7L));
        when(router.adapterFor("local")).thenReturn(adapter);
        org.mockito.Mockito.doThrow(new RuntimeException("io")).when(adapter).delete(anyString());

        assertThatThrownBy(() -> service.delete(7L, 5L)).isInstanceOf(RuntimeException.class);
        org.mockito.Mockito.verify(fileMapper, org.mockito.Mockito.never()).deleteById(anyLong());
    }

    @Test
    void confirmMarksFinalFrameAndLinksFormalRecord() {
        when(fileMapper.selectById(5L)).thenReturn(visibleRow(7L));
        when(scopeResolver.resolveAccess(7L)).thenReturn(SyncAccessScope.singleUser(7L));
        when(fileMapper.updateById(any(BlMediaFile.class))).thenReturn(1);
        MediaConfirmCmd cmd = new MediaConfirmCmd();
        cmd.setLinkedRecordType("VISIT");
        cmd.setLinkedRecordId(888L);

        MediaFileVO vo = service.confirm(7L, 5L, cmd);

        assertThat(vo.getIsFinalFrame()).isEqualTo(1);
        assertThat(vo.getLinkedRecordType()).isEqualTo("VISIT");
        assertThat(vo.getLinkedRecordId()).isEqualTo(888L);
        assertThat(vo.getConfirmedAt()).isNotNull();
        verify(fileMapper).updateById(any(BlMediaFile.class));
    }

    private static BlMediaQuota quota(long initialBytes) {
        BlMediaQuota quota = new BlMediaQuota();
        quota.setChildId(100L);
        quota.setUserId(7L);
        quota.setInitialBytes(initialBytes);
        return quota;
    }

    private static BlMediaFile visibleRow(long ownerUserId) {
        BlMediaFile row = new BlMediaFile();
        row.setId(5L);
        row.setChildId(100L);
        row.setUserId(ownerUserId);
        row.setStorageBackend("local");
        row.setObjectKey("key-5");
        row.setSizeBytes(10L);
        row.setIsFinalFrame(0);
        return row;
    }
}
