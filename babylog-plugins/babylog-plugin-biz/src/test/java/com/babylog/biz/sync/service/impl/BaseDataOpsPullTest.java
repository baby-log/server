package com.babylog.biz.sync.service.impl;

import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.BaseDataTable;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 基础数据通道运营表（M40）：疫苗时间表 / 异常提醒阈值 / 版本与公告。
 *
 * <p>契约与 products 同构（游标推进、空页退回、分页截断），区别只有 changes 键与注册表项；
 * 每张表独立端点、App 端独立游标，互不干扰。
 */
@ExtendWith(MockitoExtension.class)
class BaseDataOpsPullTest {

    @Mock
    private SyncRecordStore store;

    @InjectMocks
    private BaseDataServiceImpl service;

    @Test
    void vaccineSchedule_usesRegistryTableAndReturnsRowsUnderVaccineScheduleKey() {
        when(store.selectBaseChanges(eq(BaseDataTable.VACCINE_SCHEDULE), any(LocalDateTime.class), anyLong(), eq(100)))
                .thenReturn(List.of(
                        baseRow(BaseDataTable.VACCINE_SCHEDULE, 1L, 1000L,
                                Map.of("id", 1L, "vaccineName", "乙肝疫苗", "doseNo", 1)),
                        baseRow(BaseDataTable.VACCINE_SCHEDULE, 2L, 1000L,
                                Map.of("id", 2L, "vaccineName", "卡介苗", "doseNo", 1))));

        SyncChangesVO vo = service.pullVaccineSchedule(0L, 0L, null);

        assertThat(vo.getCount()).isEqualTo(2);
        assertThat(vo.isHasMore()).isFalse();
        assertThat(vo.getChanges()).containsOnlyKeys("vaccine_schedule");
        assertThat(vo.getChanges().get("vaccine_schedule")).hasSize(2);
        assertThat(vo.getNextSince()).isEqualTo(1000L);
        assertThat(vo.getNextSinceId()).isEqualTo(2L);
    }

    @Test
    void alertThresholds_returnsKeyValueRowsUnderAlertThresholdsKey() {
        when(store.selectBaseChanges(eq(BaseDataTable.ALERT_THRESHOLDS), any(LocalDateTime.class), anyLong(), anyInt()))
                .thenReturn(List.of(
                        baseRow(BaseDataTable.ALERT_THRESHOLDS, 2L, 2000L,
                                Map.of("id", 2L, "thresholdKey", "milk_drop_ratio", "thresholdValue", "0.7"))));

        SyncChangesVO vo = service.pullAlertThresholds(0L, 0L, null);

        assertThat(vo.getChanges()).containsOnlyKeys("alert_thresholds");
        assertThat(vo.getChanges().get("alert_thresholds").get(0))
                .containsEntry("thresholdKey", "milk_drop_ratio")
                .containsEntry("thresholdValue", "0.7");
        assertThat(vo.getNextSinceId()).isEqualTo(2L);
    }

    @Test
    void versions_delegatesToAppVersionsRegistryEntry() {
        // PUBLISHED 谓词不在服务层而在存储层（SyncViewRegistry 类型化规格钉死），
        // 这里只锁「路由到 APP_VERSIONS 注册项 + changes 键 app_versions」
        when(store.selectBaseChanges(eq(BaseDataTable.APP_VERSIONS), any(LocalDateTime.class), anyLong(), anyInt()))
                .thenReturn(List.of());

        SyncChangesVO vo = service.pullPublishedVersions(777L, 3L, null);

        verify(store).selectBaseChanges(eq(BaseDataTable.APP_VERSIONS), any(LocalDateTime.class), eq(3L), eq(100));
        assertThat(vo.getChanges()).containsOnlyKeys("app_versions");
        // 空页退回入参游标（与 products 同款铁律）
        assertThat(vo.getNextSince()).isEqualTo(777L);
        assertThat(vo.getNextSinceId()).isEqualTo(3L);
    }

    @Test
    void recordTypeRules_delegatesToRegistryEntryUnderRecordTypeRulesKey() {
        // M41：路由到 RECORD_TYPE_RULES 注册项 + changes 键 record_type_rules；
        // 行字段原样透传（enabled 的 null 语义由客户端合并层解释）
        when(store.selectBaseChanges(eq(BaseDataTable.RECORD_TYPE_RULES), any(LocalDateTime.class), anyLong(), anyInt()))
                .thenReturn(List.of(
                        baseRow(BaseDataTable.RECORD_TYPE_RULES, 4L, 3000L,
                                Map.of("id", 4L, "recordType", "SOLID", "category", "FEEDING",
                                        "startAgeDays", 180, "sortOrder", 40, "displayLabel", "辅食")),
                        baseRow(BaseDataTable.RECORD_TYPE_RULES, 6L, 3000L,
                                Map.of("id", 6L, "recordType", "PUMP", "category", "FEEDING",
                                        "enabled", 0, "endAgeDays", 730, "sortOrder", 60))));

        SyncChangesVO vo = service.pullRecordTypeRules(0L, 0L, null);

        verify(store).selectBaseChanges(eq(BaseDataTable.RECORD_TYPE_RULES), any(LocalDateTime.class), eq(0L), eq(100));
        assertThat(vo.getChanges()).containsOnlyKeys("record_type_rules");
        assertThat(vo.getChanges().get("record_type_rules")).hasSize(2);
        assertThat(vo.getChanges().get("record_type_rules").get(0))
                .containsEntry("recordType", "SOLID")
                .containsEntry("displayLabel", "辅食");
        assertThat(vo.getNextSince()).isEqualTo(3000L);
        assertThat(vo.getNextSinceId()).isEqualTo(6L);
    }

    @Test
    void fullPageSetsHasMoreAndTruncates() {
        when(store.selectBaseChanges(any(), any(), anyLong(), eq(1)))
                .thenReturn(List.of(
                        baseRow(BaseDataTable.VACCINE_SCHEDULE, 1L, 1000L, Map.of("id", 1L)),
                        baseRow(BaseDataTable.VACCINE_SCHEDULE, 2L, 1000L, Map.of("id", 2L))));

        SyncChangesVO vo = service.pullVaccineSchedule(0L, 0L, 1);

        assertThat(vo.isHasMore()).isTrue();
        assertThat(vo.getCount()).isEqualTo(1);
        assertThat(vo.getNextSinceId()).isEqualTo(1L);
    }

    private static SyncRecordStore.BaseRow baseRow(
            BaseDataTable table, long id, long cursorMillis, Map<String, Object> data) {
        return new SyncRecordStore.BaseRow(table, id, cursorMillis, data);
    }
}
