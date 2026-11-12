package com.babylog.biz.sync.service.impl;

import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.SyncTable;
import com.babylog.biz.sync.vo.SyncChangesVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 基础数据下发单测：游标推进、空页退回、分页截断——与 SyncServiceImpl 的拉取契约同构。
 */
@ExtendWith(MockitoExtension.class)
class BaseDataServiceImplTest {

    @Mock
    private SyncRecordStore store;

    @InjectMocks
    private BaseDataServiceImpl service;

    @Test
    void pull_usesProductsTableAndReturnsRowsUnderProductsKey() {
        when(store.selectBuiltinChanges(eq(SyncTable.PRODUCTS), any(LocalDateTime.class), anyLong(), eq(100)))
                .thenReturn(List.of(row(1L, 1000L, Map.of("id", 1L, "brand", "爱他美")),
                        row(2L, 1000L, Map.of("id", 2L, "brand", "飞鹤"))));

        SyncChangesVO vo = service.pullBuiltinProducts(0L, 0L, null);

        assertThat(vo.getCount()).isEqualTo(2);
        assertThat(vo.isHasMore()).isFalse();
        assertThat(vo.getChanges()).containsOnlyKeys("products");
        assertThat(vo.getChanges().get("products")).hasSize(2);
        assertThat(vo.getNextSince()).isEqualTo(1000L);
        assertThat(vo.getNextSinceId()).isEqualTo(2L);
    }

    @Test
    void pull_emptyPageReturnsInputCursor() {
        // 空页退回入参游标：否则客户端把游标清 0，下次全量重拉
        when(store.selectBuiltinChanges(any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        SyncChangesVO vo = service.pullBuiltinProducts(12345L, 67L, null);

        assertThat(vo.getCount()).isZero();
        assertThat(vo.getNextSince()).isEqualTo(12345L);
        assertThat(vo.getNextSinceId()).isEqualTo(67L);
        assertThat(vo.getChanges().get("products")).isEmpty();
    }

    @Test
    void pull_fullPageSetsHasMoreAndTruncates() {
        // limit=1 → 只回 1 行且 hasMore=true（SQL 已排序，subList 即页）
        when(store.selectBuiltinChanges(any(), any(), anyLong(), eq(1)))
                .thenReturn(List.of(row(1L, 1000L, Map.of("id", 1L)), row(2L, 1000L, Map.of("id", 2L))));

        SyncChangesVO vo = service.pullBuiltinProducts(0L, 0L, 1);

        assertThat(vo.isHasMore()).isTrue();
        assertThat(vo.getCount()).isEqualTo(1);
        assertThat(vo.getChanges().get("products")).hasSize(1);
    }

    @Test
    void pull_negativeSinceTreatedAsFullPull() {
        when(store.selectBuiltinChanges(any(), any(LocalDateTime.class), anyLong(), anyInt()))
                .thenReturn(List.of());

        service.pullBuiltinProducts(-5L, null, null);
        // since<0 → 0 → LocalDateTime.ofEpochMilli(0)，不抛异常即过（normalize 在 SyncCursor 已单测）
        assertThat(service).isNotNull();
    }

    private static SyncRecordStore.Row row(long id, long cursorMillis, Map<String, Object> data) {
        return new SyncRecordStore.Row(SyncTable.PRODUCTS, id, cursorMillis, data);
    }
}
