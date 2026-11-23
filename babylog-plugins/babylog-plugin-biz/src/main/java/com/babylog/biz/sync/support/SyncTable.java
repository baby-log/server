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
import com.babylog.biz.entity.BlProductPref;
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
 * 同步表注册表：19 张同步表的<b>唯一</b>清单（M67 新增 product_prefs）。
 *
 * <p>一张表要在同步协议里可用，必须在此登记——登记内容决定四件事：
 * <ol>
 *   <li>{@link #apiName}：JSON 里的表标识，<b>与 App 端 Room 表名逐字相同</b>
 *       （feedings / record_type_settings …），客户端不用做映射表；</li>
 *   <li>{@link #tableName}：库表名（bl_ 前缀，A8 白名单管辖）；</li>
 *   <li>{@link #entityClass}：列清单与同步视图实体（{@code com.babylog.biz.sync.entity}）
 *       的来源，新增列只改实体 + 迁移；</li>
 *   <li>{@link #category}：拉取过滤分类，{@code null} = 不参与分类过滤。</li>
 * </ul>
 *
 * <p>SQL 一律由 {@code MybatisPlusSyncRecordStore} 的类型化 Handler
 * （LambdaQueryWrapper/LambdaUpdateWrapper）生成，本枚举不再携带任何 SQL 片段。
 *
 * <p><b>为什么不用 24 张表全量同步</b>：bl_family / bl_device / bl_feedback / bl_app_version /
 * bl_vaccine_schedule / bl_ai_quota 是服务端专属或运营表，App 端没有对应 Room 表，
 * 走各自的业务端点（设备管理、反馈提交、版本检查），不进同步通道。
 */
public enum SyncTable {

    /**
     * 宝宝档案（基础设施：业务记录都挂 child_id，必须随行下发）。
     *
     * <p>{@code familyId} 是服务端独占列（V2.1 家庭回填归属用）：客户端 Room 没有这列、
     * 不感知也不回传（README 已文档化的结构性差异 #2）。若允许它出现在载荷里，客户端
     * 编辑宝宝后整行重推会把回填好的 family_id 覆写成 null——家庭归属 silently 丢失。
     */
    CHILDREN("children", "bl_children", BlChild.class, null, java.util.Set.of("familyId")),
    /** 记录类型开关覆盖（基础设施：决定 App 首页显示哪些记录类型） */
    RECORD_TYPE_SETTINGS("record_type_settings", "bl_record_type_settings", BlRecordTypeSetting.class, null, java.util.Set.of()),
    /**
     * 家庭成员（服务端管理表，M16）：家庭服务（create/join/changeRole/kick）维护，
     * 客户端只从 changes 拉取、push 一律 8350 拒收——否则成员可整行重推自我改成 OWNER
     * （归属列 user_id = 成员自己，8344 归属校验拦不住自己的行）。
     */
    FAMILY_MEMBERS("family_members", "bl_family_members", BlFamilyMember.class, null, java.util.Set.of()),
    /**
     * 成员分类权限（服务端管理表，M16）：同 {@link #FAMILY_MEMBERS}，由家庭服务按角色矩阵
     * 生成与原地更新（改角色 = 全量重写 can_read/can_write），客户端推送一律 8350 拒收。
     */
    MEMBER_PERMISSIONS("member_permissions", "bl_member_permissions", BlMemberPermission.class, null, java.util.Set.of()),
    /** 产品库（基础设施：内置行服务端下发，自定义行用户自己维护） */
    PRODUCTS("products", "bl_products", BlProduct.class, null, java.util.Set.of()),
    /**
     * 产品偏好（基础设施，M67 第 19 张表）：启停/默认/上次使用，家庭共享语义——
     * 行随 visibleUserIds 下发、家庭内成员可见可改（M16 家庭维度归属放行）。
     * 内置产品（id 1~99）的偏好也存这里：偏好行自身 id 是新雪花，不触发
     * {@code PRODUCTS} 专属的内置段 8345 拒推。
     */
    PRODUCT_PREFS("product_prefs", "bl_product_prefs", BlProductPref.class, null, java.util.Set.of()),
    /** 提醒（基础设施：跨分类，App 靠它驱动本地通知） */
    REMINDERS("reminders", "bl_reminders", BlReminder.class, null, java.util.Set.of()),

    /**
     * 喂养（M37 计时器）。
     *
     * <p>M53 起进行中计时行（{@code in_progress = 1}）正常参与同步下发：一台设备
     * 开始喝奶后，其它终端的「进行中」卡才有数据来源；四端「in_progress=1 永不进
     * outbox」红线同步解除。多设备同时开计时产生的重复行，由 {@code SyncServiceImpl}
     * 在收到结束行时按 (user, child, type) 软删其它进行中行收敛。
     */
    FEEDINGS("feedings", "bl_feedings", BlFeeding.class, SyncCategory.FEEDING, java.util.Set.of()),

    /** 尿布 */
    DIAPERS("diapers", "bl_diapers", BlDiaper.class, SyncCategory.CARE, java.util.Set.of()),
    /** 睡眠 */
    SLEEPS("sleeps", "bl_sleeps", BlSleep.class, SyncCategory.CARE, java.util.Set.of()),
    /** 洗澡 */
    BATHS("baths", "bl_baths", BlBath.class, SyncCategory.CARE, java.util.Set.of()),
    /** 吐奶 */
    SPITS("spits", "bl_spits", BlSpit.class, SyncCategory.CARE, java.util.Set.of()),
    /** 泵奶 */
    PUMP_SESSIONS("pump_sessions", "bl_pump_sessions", BlPumpSession.class, SyncCategory.CARE, java.util.Set.of()),

    /** 测量（体重/身高/头围/体温/黄疸） */
    MEASUREMENTS("measurements", "bl_measurements", BlMeasurement.class, SyncCategory.HEALTH, java.util.Set.of()),

    /** 用药/补剂 */
    MEDICATIONS("medications", "bl_medications", BlMedication.class, SyncCategory.MEDICAL, java.util.Set.of()),
    /** 疫苗接种实例 */
    VACCINATIONS("vaccinations", "bl_vaccinations", BlVaccination.class, SyncCategory.MEDICAL, java.util.Set.of()),
    /** 随访/复查 */
    FOLLOW_UPS("follow_ups", "bl_follow_ups", BlFollowUp.class, SyncCategory.MEDICAL, java.util.Set.of()),
    /** 就医 */
    VISITS("visits", "bl_visits", BlVisit.class, SyncCategory.MEDICAL, java.util.Set.of()),
    /** 附件（宿主多为就医/疫苗/测量，统一归医疗分类） */
    ATTACHMENTS("attachments", "bl_attachments", BlAttachment.class, SyncCategory.MEDICAL, java.util.Set.of()),
    ;

    private final String apiName;
    private final String tableName;
    private final Class<? extends BlSyncDO> entityClass;
    private final SyncCategory category;
    /** 本表额外的服务端独占属性（payload 与 pull 都排除；family_members 的 familyId 不在此列——它是客户端可推的载荷列） */
    private final java.util.Set<String> serverOwnedProperties;

    /** 服务端管理表（M16）：这两张由家庭服务维护，客户端只拉不推（push 拒收 8350） */
    private static final java.util.Set<SyncTable> SERVER_MANAGED =
            java.util.Set.of(FAMILY_MEMBERS, MEMBER_PERMISSIONS);

    SyncTable(String apiName, String tableName, Class<? extends BlSyncDO> entityClass, SyncCategory category,
              java.util.Set<String> serverOwnedProperties) {
        this.apiName = apiName;
        this.tableName = tableName;
        this.entityClass = entityClass;
        this.category = category;
        this.serverOwnedProperties = java.util.Set.copyOf(serverOwnedProperties);
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

    /** 本表的服务端独占属性（不进载荷、不回传；如 bl_children.family_id 由家庭服务回填） */
    public java.util.Set<String> serverOwnedProperties() {
        return serverOwnedProperties;
    }

    /**
     * 是否服务端管理表（family_members / member_permissions）：push 整表拒收（8350），
     * 拉取照常随行下发。这是 M16 提权漏洞的收口：这两表的行归属 = 成员自己，
     * 不拒收则成员可整行重推自我改成 OWNER / 翻转 canWrite。
     */
    public boolean isServerManaged() {
        return SERVER_MANAGED.contains(this);
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
