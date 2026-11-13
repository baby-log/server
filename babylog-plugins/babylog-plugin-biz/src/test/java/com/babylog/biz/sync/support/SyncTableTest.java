package com.babylog.biz.sync.support;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 同步表注册表单测：这张清单是协议与数据库之间的唯一约定，登记错了客户端就拉不到数据。
 */
class SyncTableTest {

    @Test
    void registryCoversExactlyTheEighteenSyncTables() {
        assertThat(SyncTable.values()).hasSize(18);
    }

    @Test
    void apiNameMatchesRoomTableNameAndTableNameKeepsBlPrefix() {
        for (SyncTable table : SyncTable.values()) {
            // 表标识与 Room 表名逐字相同（App 端不用做映射），库表名是其 bl_ 前缀版本
            assertThat(table.tableName()).isEqualTo("bl_" + table.apiName());
            // 实体必须挂在 BlSyncDO 下，否则反射推导不到同步三件套（version/deleted/server_updated_at）
            assertThat(table.entityClass()).isAssignableTo(com.babylog.biz.entity.BlSyncDO.class);
        }
    }

    @Test
    void infrastructureTablesIgnoreCategoryFilter() {
        Set<SyncTable> feedingOnly = SyncTable.forCategories(Set.of(SyncCategory.FEEDING));

        assertThat(feedingOnly).contains(SyncTable.FEEDINGS);
        // 基础设施表（无分类）任何时候都要下发，否则拉回来的业务记录连 child_id 都对不上
        assertThat(feedingOnly).contains(SyncTable.CHILDREN, SyncTable.PRODUCTS, SyncTable.REMINDERS,
                SyncTable.RECORD_TYPE_SETTINGS, SyncTable.FAMILY_MEMBERS, SyncTable.MEMBER_PERMISSIONS);
        // 分类过滤生效：护理/健康/医疗的表示例都不该出现
        assertThat(feedingOnly).doesNotContain(SyncTable.DIAPERS, SyncTable.MEASUREMENTS, SyncTable.VISITS);
    }

    @Test
    void nullCategoriesMeansEverything() {
        assertThat(SyncTable.forCategories(null)).hasSize(18);
    }

    @Test
    void emptyCategorySetStillReturnsInfrastructureTables() {
        Set<SyncTable> none = SyncTable.forCategories(Set.of());

        assertThat(none).doesNotContain(SyncTable.FEEDINGS, SyncTable.DIAPERS);
        assertThat(none).contains(SyncTable.CHILDREN);
    }

    @Test
    void lookupByApiNameIsCaseSensitive() {
        assertThat(SyncTable.byApiName("record_type_settings")).contains(SyncTable.RECORD_TYPE_SETTINGS);
        assertThat(SyncTable.byApiName("Feedings")).isEmpty();
        assertThat(SyncTable.byApiName(null)).isEmpty();
    }

    @Test
    void categoryParsingAcceptsLowerCaseAndRejectsUnknown() {
        assertThat(SyncCategory.parse("feeding, care")).containsExactlyInAnyOrder(
                SyncCategory.FEEDING, SyncCategory.CARE);
        assertThat(SyncCategory.parse("")).isNull();
        assertThat(SyncCategory.parse(null)).isNull();
        assertThatThrownBy(() -> SyncCategory.parse("FEEDING,UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void serverManagedTablesAreExactlyMembersAndPermissions() {
        // M16 提权漏洞收口：这两表的行归属 = 成员自己，push 整表拒收（8350），拉取照常。
        // 清单锁死：多登记一张会误伤正常数据表，少登记一张会重新打开提权口子
        assertThat(SyncTable.FAMILY_MEMBERS.isServerManaged()).isTrue();
        assertThat(SyncTable.MEMBER_PERMISSIONS.isServerManaged()).isTrue();
        for (SyncTable table : SyncTable.values()) {
            if (table == SyncTable.FAMILY_MEMBERS || table == SyncTable.MEMBER_PERMISSIONS) {
                continue;
            }
            assertThat(table.isServerManaged())
                    .as("表 %s 不应被标记为服务端管理表", table.apiName())
                    .isFalse();
        }
    }
}
