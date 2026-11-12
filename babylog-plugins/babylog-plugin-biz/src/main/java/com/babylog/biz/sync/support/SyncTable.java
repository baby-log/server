package com.babylog.biz.sync.support;

import com.babylog.biz.entity.BlAttachment;
import com.babylog.biz.entity.BlBath;
import com.babylog.biz.entity.BlChild;
import com.babylog.biz.entity.BlDiaper;
import com.babylog.biz.entity.BlFamilyMember;
import com.babylog.biz.entity.BlFeeding;
import com.babylog.biz.entity.BlFollowUp;
import com.babylog.biz.entity.BlMeasurement;
import com.babylog.biz.entity.BlMedication;
import com.babylog.biz.entity.BlMemberPermission;
import com.babylog.biz.entity.BlProduct;
import com.babylog.biz.entity.BlPumpSession;
import com.babylog.biz.entity.BlRecordTypeSetting;
import com.babylog.biz.entity.BlReminder;
import com.babylog.biz.entity.BlSleep;
import com.babylog.biz.entity.BlSpit;
import com.babylog.biz.entity.BlSyncDO;
import com.babylog.biz.entity.BlVaccination;
import com.babylog.biz.entity.BlVisit;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 同步表注册表：18 张同步表的<b>唯一</b>清单。
 *
 * <p>一张表要在同步协议里可用，必须在此登记——登记内容决定四件事：
 * <ol>
 *   <li>{@link #apiName}：JSON 里的表标识，<b>与 App 端 Room 表名逐字相同</b>
 *       （feedings / record_type_settings …），客户端不用做映射表；</li>
 *   <li>{@link #tableName}：库表名（bl_ 前缀，A8 白名单管辖）；</li>
 *   <li>{@link #entityClass}：列清单的来源。SQL 列名由实体字段反射推导
 *       （见 {@code SyncTableSql}），新增列只改实体 + 迁移，不必回来改 SQL；</li>
 *   <li>{@link #category}：拉取过滤分类，{@code null} = 不参与分类过滤。</li>
 * </ol>
 *
 * <p><b>为什么不用 24 张表全量同步</b>：bl_family / bl_device / bl_feedback / bl_app_version /
 * bl_vaccine_schedule / bl_ai_quota 是服务端专属或运营表，App 端没有对应 Room 表，
 * 走各自的业务端点（设备管理、反馈提交、版本检查），不进同步通道。
 */
public enum SyncTable {

    /** 宝宝档案（基础设施：业务记录都挂 child_id，必须随行下发） */
    CHILDREN("children", "bl_children", BlChild.class, null),
    /** 记录类型开关覆盖（基础设施：决定 App 首页显示哪些记录类型） */
    RECORD_TYPE_SETTINGS("record_type_settings", "bl_record_type_settings", BlRecordTypeSetting.class, null),
    /** 家庭成员（基础设施：created_by 显示名靠它翻译） */
    FAMILY_MEMBERS("family_members", "bl_family_members", BlFamilyMember.class, null),
    /** 成员分类权限（基础设施：V2.1 权限矩阵，V2.0 只有 owner 一行） */
    MEMBER_PERMISSIONS("member_permissions", "bl_member_permissions", BlMemberPermission.class, null),
    /** 产品库（基础设施：内置行服务端下发，自定义行用户自己维护） */
    PRODUCTS("products", "bl_products", BlProduct.class, null),
    /** 提醒（基础设施：跨分类，App 靠它驱动本地通知） */
    REMINDERS("reminders", "bl_reminders", BlReminder.class, null),

    /** 喂养 */
    FEEDINGS("feedings", "bl_feedings", BlFeeding.class, SyncCategory.FEEDING),

    /** 尿布 */
    DIAPERS("diapers", "bl_diapers", BlDiaper.class, SyncCategory.CARE),
    /** 睡眠 */
    SLEEPS("sleeps", "bl_sleeps", BlSleep.class, SyncCategory.CARE),
    /** 洗澡 */
    BATHS("baths", "bl_baths", BlBath.class, SyncCategory.CARE),
    /** 吐奶 */
    SPITS("spits", "bl_spits", BlSpit.class, SyncCategory.CARE),
    /** 泵奶 */
    PUMP_SESSIONS("pump_sessions", "bl_pump_sessions", BlPumpSession.class, SyncCategory.CARE),

    /** 测量（体重/身高/头围/体温/黄疸） */
    MEASUREMENTS("measurements", "bl_measurements", BlMeasurement.class, SyncCategory.HEALTH),

    /** 用药/补剂 */
    MEDICATIONS("medications", "bl_medications", BlMedication.class, SyncCategory.MEDICAL),
    /** 疫苗接种实例 */
    VACCINATIONS("vaccinations", "bl_vaccinations", BlVaccination.class, SyncCategory.MEDICAL),
    /** 随访/复查 */
    FOLLOW_UPS("follow_ups", "bl_follow_ups", BlFollowUp.class, SyncCategory.MEDICAL),
    /** 就医 */
    VISITS("visits", "bl_visits", BlVisit.class, SyncCategory.MEDICAL),
    /** 附件（宿主多为就医/疫苗/测量，统一归医疗分类） */
    ATTACHMENTS("attachments", "bl_attachments", BlAttachment.class, SyncCategory.MEDICAL),
    ;

    private final String apiName;
    private final String tableName;
    private final Class<? extends BlSyncDO> entityClass;
    private final SyncCategory category;

    SyncTable(String apiName, String tableName, Class<? extends BlSyncDO> entityClass, SyncCategory category) {
        this.apiName = apiName;
        this.tableName = tableName;
        this.entityClass = entityClass;
        this.category = category;
    }

    public String apiName() {
        return apiName;
    }

    public String tableName() {
        return tableName;
    }

    public Class<? extends BlSyncDO> entityClass() {
        return entityClass;
    }

    /** 拉取过滤分类；{@code null} = 不参与过滤，任何请求都下发 */
    public SyncCategory category() {
        return category;
    }

    /** 客户端传的表标识 → 注册表项（大小写敏感：与 Room 表名逐字相同） */
    public static Optional<SyncTable> byApiName(String apiName) {
        if (apiName == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(t -> t.apiName.equals(apiName)).findFirst();
    }

    /**
     * 本次拉取涉及的表。
     *
     * @param categories {@code null} = 全部；否则为请求分类（基础设施表始终包含）
     */
    public static Set<SyncTable> forCategories(Set<SyncCategory> categories) {
        Set<SyncTable> tables = EnumSet.noneOf(SyncTable.class);
        for (SyncTable table : values()) {
            if (table.category == null || categories == null || categories.contains(table.category)) {
                tables.add(table);
            }
        }
        return tables;
    }
}
