package com.babylog.biz.sync.store;

import com.babylog.biz.sync.entity.BlAlertThresholdSyncView;
import com.babylog.biz.sync.entity.BlAppVersionSyncView;
import com.babylog.biz.sync.entity.BlAttachmentSyncView;
import com.babylog.biz.sync.entity.BlBathSyncView;
import com.babylog.biz.sync.entity.BlChildSyncView;
import com.babylog.biz.sync.entity.BlDiaperSyncView;
import com.babylog.biz.sync.entity.BlFamilyMemberSyncView;
import com.babylog.biz.sync.entity.BlFeedingSyncView;
import com.babylog.biz.sync.entity.BlFollowUpSyncView;
import com.babylog.biz.sync.entity.BlMeasurementSyncView;
import com.babylog.biz.sync.entity.BlMedicationSyncView;
import com.babylog.biz.sync.entity.BlMemberPermissionSyncView;
import com.babylog.biz.sync.entity.BlProductSyncView;
import com.babylog.biz.sync.entity.BlProductPrefSyncView;
import com.babylog.biz.sync.entity.BlPumpSessionSyncView;
import com.babylog.biz.sync.entity.BlRecordTypeRuleSyncView;
import com.babylog.biz.sync.entity.BlRecordTypeSettingSyncView;
import com.babylog.biz.sync.entity.BlReminderSyncView;
import com.babylog.biz.sync.entity.BlSleepSyncView;
import com.babylog.biz.sync.entity.BlSpitSyncView;
import com.babylog.biz.sync.entity.BlVaccinationSyncView;
import com.babylog.biz.sync.entity.BlVaccineScheduleSyncView;
import com.babylog.biz.sync.entity.BlVisitSyncView;
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
import com.babylog.biz.sync.mapper.BlProductPrefSyncViewMapper;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 同步/base 通道类型化 Handler 的装配（PivotOS 严格整改 S3）。
 *
 * <p>18 张同步表 + 4 张 base 运营表逐表注册：视图 Mapper（包扫描自动注册）→
 * {@link GenericSyncTableHandler}/{@link GenericBaseTableHandler}。
 * 显式 @Bean 而非按名扫描：漏一张表 = 编译期/启动期错误，绝不静默缺表。
 * 注册规格（谓词/NOT NULL 列）唯一数据源是 {@link SyncViewRegistry}。
 */
@Configuration
public class SyncStoreHandlerConfiguration {

    // ==================== 18 张同步表 ====================

    @Bean
    public SyncTableHandler childrenSyncTableHandler(BlChildSyncViewMapper mapper) {
        return sync(SyncTable.CHILDREN, mapper, BlChildSyncView.class);
    }

    @Bean
    public SyncTableHandler recordTypeSettingsSyncTableHandler(BlRecordTypeSettingSyncViewMapper mapper) {
        return sync(SyncTable.RECORD_TYPE_SETTINGS, mapper, BlRecordTypeSettingSyncView.class);
    }

    @Bean
    public SyncTableHandler familyMembersSyncTableHandler(BlFamilyMemberSyncViewMapper mapper) {
        return sync(SyncTable.FAMILY_MEMBERS, mapper, BlFamilyMemberSyncView.class);
    }

    @Bean
    public SyncTableHandler memberPermissionsSyncTableHandler(BlMemberPermissionSyncViewMapper mapper) {
        return sync(SyncTable.MEMBER_PERMISSIONS, mapper, BlMemberPermissionSyncView.class);
    }

    @Bean
    public SyncTableHandler productsSyncTableHandler(BlProductSyncViewMapper mapper) {
        return sync(SyncTable.PRODUCTS, mapper, BlProductSyncView.class);
    }

    @Bean
    public SyncTableHandler productPrefsSyncTableHandler(BlProductPrefSyncViewMapper mapper) {
        return sync(SyncTable.PRODUCT_PREFS, mapper, BlProductPrefSyncView.class);
    }

    @Bean
    public SyncTableHandler remindersSyncTableHandler(BlReminderSyncViewMapper mapper) {
        return sync(SyncTable.REMINDERS, mapper, BlReminderSyncView.class);
    }

    @Bean
    public SyncTableHandler feedingsSyncTableHandler(BlFeedingSyncViewMapper mapper) {
        return sync(SyncTable.FEEDINGS, mapper, BlFeedingSyncView.class);
    }

    @Bean
    public SyncTableHandler diapersSyncTableHandler(BlDiaperSyncViewMapper mapper) {
        return sync(SyncTable.DIAPERS, mapper, BlDiaperSyncView.class);
    }

    @Bean
    public SyncTableHandler sleepsSyncTableHandler(BlSleepSyncViewMapper mapper) {
        return sync(SyncTable.SLEEPS, mapper, BlSleepSyncView.class);
    }

    @Bean
    public SyncTableHandler bathsSyncTableHandler(BlBathSyncViewMapper mapper) {
        return sync(SyncTable.BATHS, mapper, BlBathSyncView.class);
    }

    @Bean
    public SyncTableHandler spitsSyncTableHandler(BlSpitSyncViewMapper mapper) {
        return sync(SyncTable.SPITS, mapper, BlSpitSyncView.class);
    }

    @Bean
    public SyncTableHandler pumpSessionsSyncTableHandler(BlPumpSessionSyncViewMapper mapper) {
        return sync(SyncTable.PUMP_SESSIONS, mapper, BlPumpSessionSyncView.class);
    }

    @Bean
    public SyncTableHandler measurementsSyncTableHandler(BlMeasurementSyncViewMapper mapper) {
        return sync(SyncTable.MEASUREMENTS, mapper, BlMeasurementSyncView.class);
    }

    @Bean
    public SyncTableHandler medicationsSyncTableHandler(BlMedicationSyncViewMapper mapper) {
        return sync(SyncTable.MEDICATIONS, mapper, BlMedicationSyncView.class);
    }

    @Bean
    public SyncTableHandler vaccinationsSyncTableHandler(BlVaccinationSyncViewMapper mapper) {
        return sync(SyncTable.VACCINATIONS, mapper, BlVaccinationSyncView.class);
    }

    @Bean
    public SyncTableHandler followUpsSyncTableHandler(BlFollowUpSyncViewMapper mapper) {
        return sync(SyncTable.FOLLOW_UPS, mapper, BlFollowUpSyncView.class);
    }

    @Bean
    public SyncTableHandler visitsSyncTableHandler(BlVisitSyncViewMapper mapper) {
        return sync(SyncTable.VISITS, mapper, BlVisitSyncView.class);
    }

    @Bean
    public SyncTableHandler attachmentsSyncTableHandler(BlAttachmentSyncViewMapper mapper) {
        return sync(SyncTable.ATTACHMENTS, mapper, BlAttachmentSyncView.class);
    }

    // ==================== 4 张 base 运营表 ====================

    @Bean
    public BaseTableHandler vaccineScheduleBaseTableHandler(BlVaccineScheduleSyncViewMapper mapper) {
        return base(BaseDataTable.VACCINE_SCHEDULE, mapper, BlVaccineScheduleSyncView.class);
    }

    @Bean
    public BaseTableHandler alertThresholdsBaseTableHandler(BlAlertThresholdSyncViewMapper mapper) {
        return base(BaseDataTable.ALERT_THRESHOLDS, mapper, BlAlertThresholdSyncView.class);
    }

    @Bean
    public BaseTableHandler appVersionsBaseTableHandler(BlAppVersionSyncViewMapper mapper) {
        return base(BaseDataTable.APP_VERSIONS, mapper, BlAppVersionSyncView.class);
    }

    @Bean
    public BaseTableHandler recordTypeRulesBaseTableHandler(BlRecordTypeRuleSyncViewMapper mapper) {
        return base(BaseDataTable.RECORD_TYPE_RULES, mapper, BlRecordTypeRuleSyncView.class);
    }

    // ------------------------------------------------------------------

    private static <E extends com.babylog.biz.entity.BlSyncDO> SyncTableHandler sync(
            SyncTable table, com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper, Class<E> viewClass) {
        return new GenericSyncTableHandler<>(table, mapper, viewClass, SyncViewRegistry.syncSpec(table));
    }

    private static <E> BaseTableHandler base(BaseDataTable table,
                                             com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper,
                                             Class<E> viewClass) {
        return new GenericBaseTableHandler<>(table, mapper, viewClass, SyncViewRegistry.baseSpec(table));
    }
}
