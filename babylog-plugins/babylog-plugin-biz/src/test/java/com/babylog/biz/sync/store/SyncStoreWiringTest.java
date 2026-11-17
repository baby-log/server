package com.babylog.biz.sync.store;

import com.babylog.biz.sync.mapper.BlAlertThresholdSyncViewMapper;
import com.babylog.biz.sync.mapper.BlAppVersionSyncViewMapper;
import com.babylog.biz.sync.mapper.BlAttachmentSyncViewMapper;
import com.babylog.biz.sync.mapper.BlBathSyncViewMapper;
import com.babylog.biz.sync.mapper.BlChildSyncViewMapper;
import com.babylog.biz.sync.mapper.BlDiaperSyncViewMapper;
import com.babylog.biz.sync.mapper.BlFamilyMemberSyncViewMapper;
import com.babylog.biz.sync.mapper.BlFeedingSyncViewMapper;
import com.babylog.biz.sync.mapper.BlFollowUpSyncViewMapper;
import com.babylog.biz.sync.mapper.BlMeasurementSyncViewMapper;
import com.babylog.biz.sync.mapper.BlMedicationSyncViewMapper;
import com.babylog.biz.sync.mapper.BlMemberPermissionSyncViewMapper;
import com.babylog.biz.sync.mapper.BlProductSyncViewMapper;
import com.babylog.biz.sync.mapper.BlPumpSessionSyncViewMapper;
import com.babylog.biz.sync.mapper.BlRecordTypeRuleSyncViewMapper;
import com.babylog.biz.sync.mapper.BlRecordTypeSettingSyncViewMapper;
import com.babylog.biz.sync.mapper.BlReminderSyncViewMapper;
import com.babylog.biz.sync.mapper.BlSleepSyncViewMapper;
import com.babylog.biz.sync.mapper.BlSpitSyncViewMapper;
import com.babylog.biz.sync.mapper.BlVaccinationSyncViewMapper;
import com.babylog.biz.sync.mapper.BlVaccineScheduleSyncViewMapper;
import com.babylog.biz.sync.mapper.BlVisitSyncViewMapper;
import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncTable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 存储层接线守卫（PivotOS 严格整改 S3）：
 * <ol>
 *   <li>22 个 @Bean 装配方法用 mock Mapper 逐个实例化 Handler——任何一张表的
 *       视图实体/列推导/SFunction 解析有问题都会在这里炸（启动前拦截）；</li>
 *   <li>{@link MybatisPlusSyncRecordStore} 注册完整性 fail-fast（缺表/重表即拒）与
 *       路由、limit 钳位、契约异常逐项钉死。</li>
 * </ol>
 * 真库行为见 {@code SyncStoreH2Test}。
 */
class SyncStoreWiringTest {

    /** 用 mock Mapper 跑通全部装配方法，返回 18 个同步 Handler（与配置一一对应） */
    private static List<SyncTableHandler> wiredSyncHandlers() {
        SyncStoreHandlerConfiguration config = new SyncStoreHandlerConfiguration();
        List<SyncTableHandler> handlers = new ArrayList<>();
        handlers.add(config.childrenSyncTableHandler(Mockito.mock(BlChildSyncViewMapper.class)));
        handlers.add(config.recordTypeSettingsSyncTableHandler(Mockito.mock(BlRecordTypeSettingSyncViewMapper.class)));
        handlers.add(config.familyMembersSyncTableHandler(Mockito.mock(BlFamilyMemberSyncViewMapper.class)));
        handlers.add(config.memberPermissionsSyncTableHandler(Mockito.mock(BlMemberPermissionSyncViewMapper.class)));
        handlers.add(config.productsSyncTableHandler(Mockito.mock(BlProductSyncViewMapper.class)));
        handlers.add(config.remindersSyncTableHandler(Mockito.mock(BlReminderSyncViewMapper.class)));
        handlers.add(config.feedingsSyncTableHandler(Mockito.mock(BlFeedingSyncViewMapper.class)));
        handlers.add(config.diapersSyncTableHandler(Mockito.mock(BlDiaperSyncViewMapper.class)));
        handlers.add(config.sleepsSyncTableHandler(Mockito.mock(BlSleepSyncViewMapper.class)));
        handlers.add(config.bathsSyncTableHandler(Mockito.mock(BlBathSyncViewMapper.class)));
        handlers.add(config.spitsSyncTableHandler(Mockito.mock(BlSpitSyncViewMapper.class)));
        handlers.add(config.pumpSessionsSyncTableHandler(Mockito.mock(BlPumpSessionSyncViewMapper.class)));
        handlers.add(config.measurementsSyncTableHandler(Mockito.mock(BlMeasurementSyncViewMapper.class)));
        handlers.add(config.medicationsSyncTableHandler(Mockito.mock(BlMedicationSyncViewMapper.class)));
        handlers.add(config.vaccinationsSyncTableHandler(Mockito.mock(BlVaccinationSyncViewMapper.class)));
        handlers.add(config.followUpsSyncTableHandler(Mockito.mock(BlFollowUpSyncViewMapper.class)));
        handlers.add(config.visitsSyncTableHandler(Mockito.mock(BlVisitSyncViewMapper.class)));
        handlers.add(config.attachmentsSyncTableHandler(Mockito.mock(BlAttachmentSyncViewMapper.class)));
        return handlers;
    }

    private static List<BaseTableHandler> wiredBaseHandlers() {
        SyncStoreHandlerConfiguration config = new SyncStoreHandlerConfiguration();
        List<BaseTableHandler> handlers = new ArrayList<>();
        handlers.add(config.vaccineScheduleBaseTableHandler(Mockito.mock(BlVaccineScheduleSyncViewMapper.class)));
        handlers.add(config.alertThresholdsBaseTableHandler(Mockito.mock(BlAlertThresholdSyncViewMapper.class)));
        handlers.add(config.appVersionsBaseTableHandler(Mockito.mock(BlAppVersionSyncViewMapper.class)));
        handlers.add(config.recordTypeRulesBaseTableHandler(Mockito.mock(BlRecordTypeRuleSyncViewMapper.class)));
        return handlers;
    }

    @Test
    void allEighteenSyncAndFourBaseHandlersWireUp() {
        List<SyncTableHandler> syncHandlers = wiredSyncHandlers();
        assertThat(syncHandlers).hasSize(18);
        assertThat(syncHandlers.stream().map(SyncTableHandler::table))
                .containsExactlyInAnyOrder(SyncTable.values());
        List<BaseTableHandler> baseHandlers = wiredBaseHandlers();
        assertThat(baseHandlers).hasSize(4);
        assertThat(baseHandlers.stream().map(BaseTableHandler::table))
                .containsExactlyInAnyOrder(BaseDataTable.values());
        // 装配 + 注册完整性校验全部通过
        new MybatisPlusSyncRecordStore(syncHandlers, baseHandlers);
    }

    @Test
    void storeRejectsMissingOrDuplicateRegistration() {
        List<SyncTableHandler> handlers = wiredSyncHandlers();
        List<SyncTableHandler> missing = new ArrayList<>(handlers);
        missing.removeIf(h -> h.table() == SyncTable.FEEDINGS);
        assertThatIllegalStateException()
                .isThrownBy(() -> new MybatisPlusSyncRecordStore(missing, wiredBaseHandlers()))
                .withMessageContaining("feedings");

        List<SyncTableHandler> duplicate = new ArrayList<>(handlers);
        duplicate.add(handlers.get(0));
        assertThatIllegalStateException()
                .isThrownBy(() -> new MybatisPlusSyncRecordStore(duplicate, wiredBaseHandlers()))
                .withMessageContaining("重复注册");
    }

    @Test
    void storeRoutesAndKeepsContractGuards() {
        SyncTableHandler feedings = Mockito.mock(SyncTableHandler.class);
        Mockito.when(feedings.table()).thenReturn(SyncTable.FEEDINGS);
        List<SyncTableHandler> handlers = new ArrayList<>(wiredSyncHandlers());
        handlers.replaceAll(h -> h.table() == SyncTable.FEEDINGS ? feedings : h);
        MybatisPlusSyncRecordStore store = new MybatisPlusSyncRecordStore(handlers, wiredBaseHandlers());

        // limit 钳位是最后一道防线（服务层已钳 [1,500]）
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.selectChanges(SyncTable.FEEDINGS, List.of(1L), LocalDateTime.now(), 0L, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.selectChanges(SyncTable.FEEDINGS, List.of(1L), LocalDateTime.now(), 0L, 1001));

        // 可见账号集合不能为空（与旧实现同异常同文案）
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.selectChanges(SyncTable.FEEDINGS, List.of(),
                        LocalDateTime.now(), 0L, 100))
                .withMessageContaining("可见账号集合不能为空");

        // upsert 路由
        Map<String, Object> payload = Map.of("id", 1L, "version", 1L);
        store.upsert(SyncTable.FEEDINGS, payload, 42L);
        Mockito.verify(feedings).upsert(payload, 42L);

        // selectExisting 空集合短路（不进 Handler）
        Mockito.when(feedings.selectExisting(List.of())).thenReturn(Map.of());
        assertThat(store.selectExisting(SyncTable.FEEDINGS, List.of())).isEmpty();
    }

    @Test
    void handlerRejectsSpecTableMismatch() {
        assertThatThrownBy(() -> new GenericSyncTableHandler<>(
                SyncTable.FEEDINGS,
                Mockito.mock(BlChildSyncViewMapper.class),
                com.babylog.biz.sync.entity.BlChildSyncView.class,
                SyncViewRegistry.syncSpec(SyncTable.CHILDREN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feedings");
    }
}
