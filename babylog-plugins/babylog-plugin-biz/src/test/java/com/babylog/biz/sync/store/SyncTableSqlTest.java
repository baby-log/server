package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.SyncTable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 骨架单测：列推导错了，联调时只会看到「Duplicate column」或某个字段永远同步不上去。
 */
class SyncTableSqlTest {

    @Test
    void everyTableDerivesACleanColumnSet() {
        for (SyncTable table : SyncTable.values()) {
            SyncTableSql sql = new SyncTableSql(table);

            // id 排在拉取列最前；载荷列不含 id（主键单独处理）
            assertThat(sql.pullProperties()).as("%s 拉取列", table.apiName())
                    .startsWith("id").doesNotHaveDuplicates();
            assertThat(sql.payloadProperties()).as("%s 载荷列", table.apiName())
                    .doesNotContain("id").doesNotHaveDuplicates()
                    // 同步三件套里的两件（server_updated_at 由库维护，不载荷）
                    .contains("version", "deleted");

            // 归属与审计列一律服务端盖章，客户端传了也不能进 SQL
            assertThat(sql.payloadProperties()).doesNotContain(
                    "userId", "ownerUserId", "serverUpdatedAt",
                    "createBy", "createTime", "updateBy", "updateTime");
        }
    }

    @Test
    void insertStatementHasNoDuplicateColumnsAndVersionAssignmentComesLast() {
        for (SyncTable table : SyncTable.values()) {
            String insertSql = new SyncTableSql(table).insertSql();
            List<String> columns = Arrays.stream(insertSql
                            .substring(insertSql.indexOf('(') + 1, insertSql.indexOf(')'))
                            .split(","))
                    .map(String::trim)
                    .toList();

            assertThat(columns).as("%s insert 列", table.apiName()).doesNotHaveDuplicates();
            assertThat(columns).allSatisfy(column -> assertThat(column)
                    .as("%s 列名应为 snake_case", table.apiName())
                    .matches("[a-z][a-z0-9_]*"));
            // MySQL 的 ODKU 赋值从左到右生效：version 若不在最后，前面所有 IF(:version > version, …) 都会判假
            assertThat(insertSql).as("%s 版本门禁必须在最后", table.apiName())
                    .endsWith("version = IF(:version > version, :version, version)");
        }
    }

    @Test
    void ownershipColumnsAreStampedNotPushed() {
        // children 有 owner_user_id（宝宝所有权），family_members 的 user_id 就是成员本身 → 两者都由服务端盖章
        assertThat(new SyncTableSql(SyncTable.CHILDREN).stampedProperties())
                .containsExactlyInAnyOrder("userId", "ownerUserId");
        assertThat(new SyncTableSql(SyncTable.FAMILY_MEMBERS).stampedProperties())
                .containsExactly("userId");
        // 普通业务表只有一个归属列
        assertThat(new SyncTableSql(SyncTable.FEEDINGS).stampedProperties()).containsExactly("userId");
    }

    @Test
    void cursorQueryUsesOwnerAndBothCursorSegments() {
        String sql = new SyncTableSql(SyncTable.FEEDINGS).selectChangesSql();

        assertThat(sql).contains("WHERE user_id = :userId")
                .contains("(server_updated_at > :since OR (server_updated_at = :since AND id > :sinceId))")
                .contains("ORDER BY server_updated_at, id")
                // 软删除行必须能被拉到：同步语义里 deleted=1 也是变更
                .doesNotContain("deleted = 0");
    }

    @Test
    void camelToSnakeHandlesAcronymsAndTailNumbers() {
        assertThat(SyncTableSql.camelToSnake("amountMl")).isEqualTo("amount_ml");
        assertThat(SyncTableSql.camelToSnake("serverUpdatedAt")).isEqualTo("server_updated_at");
        assertThat(SyncTableSql.camelToSnake("deviceUuid")).isEqualTo("device_uuid");
        assertThat(SyncTableSql.camelToSnake("note")).isEqualTo("note");
    }
}
