package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.babylog.biz.entity.BlSyncDO;
import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncTable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类型化注册规格守卫（PivotOS 严格整改 S3）：18 张同步表 + 4 张 base 运营表
 * 每张恰好注册一个视图实体，列清单/谓词/NOT NULL 口径与旧动态 SQL 实现逐条对齐。
 *
 * <p>真正的「无逻辑删除」行为由 {@code SyncStoreH2Test} 在真实 MyBatis-Plus 链路上验证；
 * 本类锁的是登记形状。
 */
class SyncViewRegistryTest {

    @Test
    void syncSpecsCoverExactlyTheEighteenTablesOnceEach() {
        assertThat(SyncViewRegistry.syncSpecs()).hasSize(18);
        assertThat(SyncViewRegistry.syncSpecs().stream().map(SyncViewRegistry.SyncSpec::table))
                .containsExactlyInAnyOrder(SyncTable.values());
        assertThat(SyncViewRegistry.syncSpecs().stream().map(SyncViewRegistry.SyncSpec::table))
                .doesNotHaveDuplicates();
    }

    @Test
    void baseSpecsCoverExactlyTheFourTablesOnceEach() {
        assertThat(SyncViewRegistry.baseSpecs()).hasSize(4);
        assertThat(SyncViewRegistry.baseSpecs().stream().map(SyncViewRegistry.BaseSpec::table))
                .containsExactlyInAnyOrder(BaseDataTable.values());
    }

    @Test
    void viewEntityNamesTheSameTableAndExtendsBusinessEntity() {
        for (SyncViewRegistry.SyncSpec spec : SyncViewRegistry.syncSpecs()) {
            TableName tableName = spec.viewClass().getAnnotation(TableName.class);
            assertThat(tableName).as("%s 必须显式 @TableName", spec.viewClass().getSimpleName()).isNotNull();
            assertThat(tableName.value()).as("%s 表名", spec.table().apiName())
                    .isEqualTo(spec.table().tableName());
            assertThat(spec.table().entityClass()).as("%s 业务实体", spec.table().apiName())
                    .isAssignableFrom(spec.viewClass());
        }
        for (SyncViewRegistry.BaseSpec spec : SyncViewRegistry.baseSpecs()) {
            TableName tableName = spec.viewClass().getAnnotation(TableName.class);
            assertThat(tableName).as("%s 必须显式 @TableName", spec.viewClass().getSimpleName()).isNotNull();
            assertThat(tableName.value()).as("%s 表名", spec.table().apiName())
                    .isEqualTo(spec.table().tableName());
            assertThat(spec.table().entityClass()).as("%s 业务实体", spec.table().apiName())
                    .isAssignableFrom(spec.viewClass());
        }
    }

    @Test
    void viewEntityShadowsLogicDeleteAndVersionWithoutTableLogic() {
        // 同步通道必须读到 deleted=0 和 deleted=1：视图实体以「遮蔽 @TableLogic 的
        // deleted 声明」明确区分，而不是 deleted IN (0,1) 之类的表面绕过
        for (SyncViewRegistry.SyncSpec spec : SyncViewRegistry.syncSpecs()) {
            Field deleted = declaredField(spec.viewClass(), "deleted");
            assertThat(deleted.getAnnotation(TableLogic.class))
                    .as("%s.deleted 不得带 @TableLogic（同步通道要看到墓碑行）", spec.viewClass().getSimpleName())
                    .isNull();
            declaredField(spec.viewClass(), "version");
            declaredField(spec.viewClass(), "serverUpdatedAt");
        }
        for (SyncViewRegistry.BaseSpec spec : SyncViewRegistry.baseSpecs()) {
            Field deleted = declaredField(spec.viewClass(), "deleted");
            assertThat(deleted.getAnnotation(TableLogic.class))
                    .as("%s.deleted 不得带 @TableLogic（base 通道下发软删除事实）", spec.viewClass().getSimpleName())
                    .isNull();
            declaredField(spec.viewClass(), "serverUpdatedAt");
        }
    }

    @Test
    void payloadAndPullColumnSetsMatchLegacyDerivation() {
        for (SyncTable table : SyncTable.values()) {
            SyncViewRegistry.SyncSpec spec = SyncViewRegistry.syncSpec(table);
            SyncEntityMeta meta = SyncEntityMeta.from(spec.viewClass(), table.serverOwnedProperties());

            // id 排在拉取列最前；载荷列不含 id，含同步两件套（serverUpdatedAt 由库维护，不载荷）
            assertThat(meta.pullProperties()).as("%s 拉取列", table.apiName())
                    .startsWith("id").doesNotHaveDuplicates();
            assertThat(meta.payloadProperties()).as("%s 载荷列", table.apiName())
                    .doesNotContain("id").doesNotHaveDuplicates()
                    .contains("version", "deleted");

            // 归属与审计列一律服务端盖章，客户端传了也不能进载荷
            assertThat(meta.payloadProperties()).as("%s 载荷列", table.apiName())
                    .doesNotContain("userId", "ownerUserId", "serverUpdatedAt",
                            "createBy", "createTime", "updateBy", "updateTime");
        }
    }

    @Test
    void stampedColumnsMatchLegacyTables() {
        // children 有 owner_user_id（宝宝所有权），family_members 的 user_id 就是成员本身
        assertThat(SyncEntityMeta.from(SyncViewRegistry.syncSpec(SyncTable.CHILDREN).viewClass(),
                SyncTable.CHILDREN.serverOwnedProperties()).stampedProperties())
                .containsExactlyInAnyOrder("userId", "ownerUserId");
        assertThat(SyncEntityMeta.from(SyncViewRegistry.syncSpec(SyncTable.FAMILY_MEMBERS).viewClass(),
                SyncTable.FAMILY_MEMBERS.serverOwnedProperties()).stampedProperties())
                .containsExactly("userId");
        assertThat(SyncEntityMeta.from(SyncViewRegistry.syncSpec(SyncTable.FEEDINGS).viewClass(),
                SyncTable.FEEDINGS.serverOwnedProperties()).stampedProperties())
                .containsExactly("userId");
    }

    @Test
    void childrenFamilyIdIsServerOwnedButFamilyMembersFamilyIdIsPayload() {
        SyncEntityMeta children = SyncEntityMeta.from(SyncViewRegistry.syncSpec(SyncTable.CHILDREN).viewClass(),
                SyncTable.CHILDREN.serverOwnedProperties());
        assertThat(children.payloadProperties()).doesNotContain("familyId");
        assertThat(children.pullProperties()).doesNotContain("familyId");

        SyncEntityMeta members = SyncEntityMeta.from(SyncViewRegistry.syncSpec(SyncTable.FAMILY_MEMBERS).viewClass(),
                SyncTable.FAMILY_MEMBERS.serverOwnedProperties());
        assertThat(members.payloadProperties()).contains("familyId");
    }

    @Test
    void predicatesAreRegisteredOnlyForFeedingsAndPublishedVersions() {
        // M37：feedings 拉取谓词 in_progress = 0（类型化等值条件，替代旧 SQL 片段）
        SyncViewRegistry.SyncSpec feedings = SyncViewRegistry.syncSpec(SyncTable.FEEDINGS);
        assertThat(feedings.predicateProperty()).isEqualTo("inProgress");
        assertThat(feedings.predicateValue()).isEqualTo(0);
        for (SyncTable table : SyncTable.values()) {
            if (table != SyncTable.FEEDINGS) {
                assertThat(SyncViewRegistry.syncSpec(table).predicateProperty())
                        .as("%s 不应有业务谓词", table.apiName()).isNull();
            }
        }
        // M10：版本表只有 PUBLISHED 行对外下发
        SyncViewRegistry.BaseSpec versions = SyncViewRegistry.baseSpec(BaseDataTable.APP_VERSIONS);
        assertThat(versions.predicateProperty()).isEqualTo("status");
        assertThat(versions.predicateValue()).isEqualTo("PUBLISHED");
        for (BaseDataTable table : BaseDataTable.values()) {
            if (table != BaseDataTable.APP_VERSIONS) {
                assertThat(SyncViewRegistry.baseSpec(table).predicateProperty())
                        .as("%s 不应有业务谓词", table.apiName()).isNull();
            }
        }
    }

    @Test
    void builtinChannelOnlyForProducts() {
        assertThat(SyncViewRegistry.builtinProperty(SyncTable.PRODUCTS)).isEqualTo("isBuiltin");
        for (SyncTable table : SyncTable.values()) {
            if (table != SyncTable.PRODUCTS) {
                assertThat(SyncViewRegistry.builtinProperty(table)).isNull();
            }
        }
    }

    @Test
    void rejectNullPropertiesAreNotNullWithDefaultFlagColumns() {
        // 对齐旧 ODKU 的库拒绝语义：显式 null 打到 NOT NULL（带默认值）列 → 拒收，不静默落默认值
        SyncViewRegistry.SyncSpec children = SyncViewRegistry.syncSpec(SyncTable.CHILDREN);
        assertThat(children.rejectNullProperties())
                .containsExactlyInAnyOrder("isPremature", "archived");
        SyncViewRegistry.SyncSpec feedings = SyncViewRegistry.syncSpec(SyncTable.FEEDINGS);
        assertThat(feedings.rejectNullProperties()).containsExactly("inProgress");
        for (SyncTable table : SyncTable.values()) {
            if (table != SyncTable.CHILDREN && table != SyncTable.FEEDINGS) {
                assertThat(SyncViewRegistry.syncSpec(table).rejectNullProperties())
                        .as("%s 不应有 NOT NULL 拒收列", table.apiName()).isEmpty();
            }
        }
        // 拒收列必须是该表的真实载荷列
        SyncEntityMeta childrenMeta = SyncEntityMeta.from(children.viewClass(), SyncTable.CHILDREN.serverOwnedProperties());
        assertThat(childrenMeta.payloadProperties()).containsAll(children.rejectNullProperties());
        SyncEntityMeta feedingsMeta = SyncEntityMeta.from(feedings.viewClass(), SyncTable.FEEDINGS.serverOwnedProperties());
        assertThat(feedingsMeta.payloadProperties()).containsAll(feedings.rejectNullProperties());
    }

    @Test
    void basePullColumnsKeepDeletedAndDropAuditAndCursor() {
        for (BaseDataTable table : BaseDataTable.values()) {
            SyncEntityMeta meta = SyncEntityMeta.from(SyncViewRegistry.baseSpec(table).viewClass(), Set.of());
            assertThat(meta.pullProperties()).as("%s 拉取列", table.apiName())
                    .startsWith("id").doesNotHaveDuplicates()
                    .contains("deleted")
                    .doesNotContain("serverUpdatedAt", "createBy", "createTime", "updateBy", "updateTime");
        }
        // M41 边界：规则表下发的可调列一个不能少
        assertThat(SyncEntityMeta.from(SyncViewRegistry.baseSpec(BaseDataTable.RECORD_TYPE_RULES).viewClass(), Set.of())
                .pullProperties())
                .contains("recordType", "category", "enabled", "startAgeDays", "endAgeDays",
                        "sortOrder", "groupKey", "displayLabel");
    }

    private static Field declaredField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(type.getSimpleName() + " 必须遮蔽/声明字段 " + name, e);
        }
    }
}
