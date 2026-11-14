package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.BaseDataTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基础数据运营表 SQL 骨架单测（M40）：列推导错了，联调时只会看到「Unknown column」
 * 或某类数据永远拉不下来——与 {@link SyncTableSqlTest} 同款防线，锁 base 通道形状。
 */
class BaseDataTableSqlTest {

    @Test
    void everyTableDerivesACleanPullColumnSet() {
        for (BaseDataTable table : BaseDataTable.values()) {
            BaseDataTableSql sql = new BaseDataTableSql(table);

            // id 排最前；审计四件套与游标列不下发；deleted 随行（软删除是客户端要应用的事实）
            assertThat(sql.pullProperties()).as("%s 拉取列", table.apiName())
                    .startsWith("id").doesNotHaveDuplicates()
                    .contains("deleted")
                    .doesNotContain("serverUpdatedAt",
                            "createBy", "createTime", "updateBy", "updateTime");
        }
    }

    @Test
    void pullSql_selectsServerUpdatedAsCursorWithDualSegmentPredicate() {
        for (BaseDataTable table : BaseDataTable.values()) {
            String selectSql = new BaseDataTableSql(table).selectChangesSql();

            assertThat(selectSql).as("%s 拉取 SQL", table.apiName())
                    .startsWith("SELECT ")
                    .contains("server_updated_at FROM " + table.tableName())
                    .contains("server_updated_at > :since")
                    .contains("server_updated_at = :since AND id > :sinceId")
                    .endsWith("ORDER BY server_updated_at, id");
            // LIMIT 由存储层内联拼接，骨架里不带
            assertThat(selectSql).doesNotContain("LIMIT");
        }
    }

    @Test
    void versionsTable_filtersPublishedOnly() {
        String selectSql = new BaseDataTableSql(BaseDataTable.APP_VERSIONS).selectChangesSql();

        // DRAFT 是后台草稿，用户不该提前看到；已发布锁定（8391）保证口径稳定
        assertThat(selectSql).contains("WHERE status = 'PUBLISHED' AND (server_updated_at > :since");
    }

    @Test
    void vaccineScheduleAndThresholds_haveNoBusinessPredicate() {
        assertThat(new BaseDataTableSql(BaseDataTable.VACCINE_SCHEDULE).selectChangesSql())
                .contains("FROM bl_vaccine_schedule WHERE (server_updated_at > :since");
        assertThat(new BaseDataTableSql(BaseDataTable.ALERT_THRESHOLDS).selectChangesSql())
                .contains("FROM bl_alert_threshold WHERE (server_updated_at > :since");
    }

    @Test
    void alertThresholds_pullsKeyValueColumns() {
        assertThat(new BaseDataTableSql(BaseDataTable.ALERT_THRESHOLDS).pullProperties())
                .contains("thresholdKey", "thresholdValue", "description");
    }
}
