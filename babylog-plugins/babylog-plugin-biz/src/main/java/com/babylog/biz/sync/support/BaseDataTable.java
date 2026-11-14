package com.babylog.biz.sync.support;

import com.babylog.biz.entity.BlAlertThreshold;
import com.babylog.biz.entity.BlAppVersion;
import com.babylog.biz.entity.BlVaccineSchedule;

/**
 * 基础数据通道（{@code /app/babylog/base/**}）的运营表注册表（M40）。
 *
 * <p>与 {@link SyncTable}（18 张同步表）<b>并列而非并入</b>：那 18 张与 App 端 Room 表
 * 一一对应、走 push/changes 双向同步；这里的三张是服务端运营表（App 端没有对应 Room 表），
 * 只经由 base 通道<b>单向下发</b>。若并进 SyncTable，它们会以「基础设施表（category=null）」
 * 身份混进 18 表同步通道被 pull 随行下发、还要回答 push 归属问题——两通道隔离的红线就破了。
 *
 * <p>登记内容与 SyncTable 同构：apiName（JSON changes 键）/ tableName / entityClass
 * （列清单来源，反射推导）/ pullPredicate（业务谓词，当前仅版本表限定 PUBLISHED）。
 * 游标口径与 products 完全一致：双段 {@code server_updated_at + id}，每类数据独立一对，
 * App 端各存各的游标，互不干扰。
 *
 * <p>三张表的 {@code server_updated_at} 均为 V2.2.12 列级 {@code ON UPDATE CURRENT_TIMESTAMP(3)}
 * 自动维护，实体刻意不声明该字段（M10/M18 教训：声明了反而会被 MP 显式写回旧值，游标不前进）。
 */
public enum BaseDataTable {

    /** 疫苗时间表（28 行 seed，V2.2.12；App 端 M40 只缓存，消费方后续批次接） */
    VACCINE_SCHEDULE("vaccine_schedule", "bl_vaccine_schedule", BlVaccineSchedule.class, ""),

    /** 异常提醒阈值（18 行 seed，键值对；客户端替换 AlertThresholds 本地默认值） */
    ALERT_THRESHOLDS("alert_thresholds", "bl_alert_threshold", BlAlertThreshold.class, ""),

    /**
     * 版本与公告：只有 PUBLISHED 行对外下发（DRAFT 是后台草稿，用户不该提前看到；
     * M10 已发布锁定 8391 保证「同一 versionCode 对外口径稳定」）。
     * 不按 platform 过滤：客户端按自己平台挑行，避免「platform 参数 × 游标分页」的组合陷阱
     * （过滤条件变了，同一对游标就不再对应稳定的时间线）。
     */
    APP_VERSIONS("app_versions", "bl_app_version", BlAppVersion.class, "status = 'PUBLISHED'"),
    ;

    private final String apiName;
    private final String tableName;
    private final Class<?> entityClass;
    /** 增量拉取的业务谓词（SQL 片段，不含 WHERE）；空串 = 无附加条件 */
    private final String pullPredicate;

    BaseDataTable(String apiName, String tableName, Class<?> entityClass, String pullPredicate) {
        this.apiName = apiName;
        this.tableName = tableName;
        this.entityClass = entityClass;
        this.pullPredicate = pullPredicate == null ? "" : pullPredicate;
    }

    /** JSON changes 键（App 端按此键分发到各自的缓存） */
    public String apiName() {
        return apiName;
    }

    public String tableName() {
        return tableName;
    }

    /** 列清单来源（反射推导，与 SyncTableSql 同款手法） */
    public Class<?> entityClass() {
        return entityClass;
    }

    /** 增量拉取的业务谓词；空串 = 无附加条件 */
    public String pullPredicate() {
        return pullPredicate;
    }
}
