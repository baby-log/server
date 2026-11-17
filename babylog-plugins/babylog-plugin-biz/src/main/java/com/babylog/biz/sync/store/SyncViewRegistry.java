package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncTable;

import java.util.List;
import java.util.Set;

/**
 * 同步/base 通道的类型化 Handler 注册规格（PivotOS 严格整改 S3）。
 *
 * <p>每张表登记四项：视图实体类、拉取业务谓词（属性名 + 等值，null = 无）、
 * 内置行通道属性（仅 products）、载荷 NOT NULL 列（显式 null 时按库拒绝语义拒收）。
 * 这里的谓词是<b>实体属性名</b>（经 {@link SyncLambdas} 变为类型化列句柄），
 * 不是 SQL 片段——S2 之前 {@code SyncTable.pullPredicate()} 那种字符串谓词已彻底移除。
 *
 * <p>本类是注册表的唯一数据源：Spring 装配（{@link SyncStoreHandlerConfiguration}）
 * 与单测共用同一份清单，「18+4 张表每张恰好注册一次」由
 * {@code SyncViewRegistryTest} 钉死。
 */
public final class SyncViewRegistry {

    /** 一张同步表的注册规格 */
    public record SyncSpec(SyncTable table,
                           Class<? extends com.babylog.biz.entity.BlSyncDO> viewClass,
                           String predicateProperty,
                           Object predicateValue,
                           Set<String> rejectNullProperties) {
    }

    /** 一张 base 运营表的注册规格 */
    public record BaseSpec(BaseDataTable table,
                           Class<?> viewClass,
                           String predicateProperty,
                           Object predicateValue) {
    }

    private static final List<SyncSpec> SYNC_SPECS = List.of(
            new SyncSpec(SyncTable.CHILDREN, com.babylog.biz.sync.entity.BlChildSyncView.class,
                    null, null, Set.of("isPremature", "archived")),
            new SyncSpec(SyncTable.RECORD_TYPE_SETTINGS,
                    com.babylog.biz.sync.entity.BlRecordTypeSettingSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.FAMILY_MEMBERS,
                    com.babylog.biz.sync.entity.BlFamilyMemberSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.MEMBER_PERMISSIONS,
                    com.babylog.biz.sync.entity.BlMemberPermissionSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.PRODUCTS, com.babylog.biz.sync.entity.BlProductSyncView.class,
                    null, null, Set.of()),
            new SyncSpec(SyncTable.REMINDERS, com.babylog.biz.sync.entity.BlReminderSyncView.class,
                    null, null, Set.of()),
            // M37 拉取谓词：进行中的计时半成品不下发（类型化等值条件，替代旧 "in_progress = 0" SQL 片段）
            new SyncSpec(SyncTable.FEEDINGS, com.babylog.biz.sync.entity.BlFeedingSyncView.class,
                    "inProgress", 0, Set.of("inProgress")),
            new SyncSpec(SyncTable.DIAPERS, com.babylog.biz.sync.entity.BlDiaperSyncView.class,
                    null, null, Set.of()),
            new SyncSpec(SyncTable.SLEEPS, com.babylog.biz.sync.entity.BlSleepSyncView.class,
                    null, null, Set.of()),
            new SyncSpec(SyncTable.BATHS, com.babylog.biz.sync.entity.BlBathSyncView.class,
                    null, null, Set.of()),
            new SyncSpec(SyncTable.SPITS, com.babylog.biz.sync.entity.BlSpitSyncView.class,
                    null, null, Set.of()),
            new SyncSpec(SyncTable.PUMP_SESSIONS,
                    com.babylog.biz.sync.entity.BlPumpSessionSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.MEASUREMENTS,
                    com.babylog.biz.sync.entity.BlMeasurementSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.MEDICATIONS,
                    com.babylog.biz.sync.entity.BlMedicationSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.VACCINATIONS,
                    com.babylog.biz.sync.entity.BlVaccinationSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.FOLLOW_UPS,
                    com.babylog.biz.sync.entity.BlFollowUpSyncView.class, null, null, Set.of()),
            new SyncSpec(SyncTable.VISITS, com.babylog.biz.sync.entity.BlVisitSyncView.class,
                    null, null, Set.of()),
            new SyncSpec(SyncTable.ATTACHMENTS,
                    com.babylog.biz.sync.entity.BlAttachmentSyncView.class, null, null, Set.of()));

    private static final List<BaseSpec> BASE_SPECS = List.of(
            new BaseSpec(BaseDataTable.VACCINE_SCHEDULE,
                    com.babylog.biz.sync.entity.BlVaccineScheduleSyncView.class, null, null),
            new BaseSpec(BaseDataTable.ALERT_THRESHOLDS,
                    com.babylog.biz.sync.entity.BlAlertThresholdSyncView.class, null, null),
            // M10 口径：DRAFT 是后台草稿，只有 PUBLISHED 行对外下发
            new BaseSpec(BaseDataTable.APP_VERSIONS,
                    com.babylog.biz.sync.entity.BlAppVersionSyncView.class, "status", "PUBLISHED"),
            new BaseSpec(BaseDataTable.RECORD_TYPE_RULES,
                    com.babylog.biz.sync.entity.BlRecordTypeRuleSyncView.class, null, null));

    public static List<SyncSpec> syncSpecs() {
        return SYNC_SPECS;
    }

    public static List<BaseSpec> baseSpecs() {
        return BASE_SPECS;
    }

    public static SyncSpec syncSpec(SyncTable table) {
        return SYNC_SPECS.stream().filter(s -> s.table() == table).findFirst()
                .orElseThrow(() -> new IllegalStateException("同步表未注册类型化规格：" + table.apiName()));
    }

    public static BaseSpec baseSpec(BaseDataTable table) {
        return BASE_SPECS.stream().filter(s -> s.table() == table).findFirst()
                .orElseThrow(() -> new IllegalStateException("base 表未注册类型化规格：" + table.apiName()));
    }

    /** 内置行通道（is_builtin=1）只对带该列的表开放：当前仅 products */
    public static String builtinProperty(SyncTable table) {
        return table == SyncTable.PRODUCTS ? "isBuiltin" : null;
    }

    private SyncViewRegistry() {
    }
}
