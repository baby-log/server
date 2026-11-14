package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.biz.sync.support.SyncTable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步记录存储实现：{@link NamedParameterJdbcTemplate} + 每表一份预生成 SQL。
 *
 * <p>实现选择说明（与 M5 的 {@code SysUserAccountDao} 划清界限）：
 * M5 那次 JdbcTemplate 直写的是 <b>sys_user</b>——别人的表，属于跨插件表结构耦合，
 * 已作为技术债销账；这里的 18 张 <b>bl_ 表是本插件自己的</b>，
 * 用通用 SQL 是因为「18 表 × 三套语句 × 动态列」用 MP 注解表达会退化成千行手写 SQL，
 * 而 MP 的 {@code @TableLogic} 又把软删除行从读写路径里过滤掉了（同步语义需要它）。
 *
 * <p>连接与事务：写操作由 {@code SyncServiceImpl#push} 的 {@code @Transactional} 统一包裹，
 * 逐条 upsert 在同一事务里，失败即整批回滚（客户端重推安全——upsert 幂等）。
 */
@Repository
public class JdbcSyncRecordStore implements SyncRecordStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<SyncTable, SyncTableSql> sqlByTable = new EnumMap<>(SyncTable.class);
    private final Map<BaseDataTable, BaseDataTableSql> sqlByBaseTable = new EnumMap<>(BaseDataTable.class);

    public JdbcSyncRecordStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        for (SyncTable table : SyncTable.values()) {
            sqlByTable.put(table, new SyncTableSql(table));
        }
        for (BaseDataTable table : BaseDataTable.values()) {
            sqlByBaseTable.put(table, new BaseDataTableSql(table));
        }
    }

    @Override
    public Map<Long, Existing> selectExisting(SyncTable table, Collection<Long> ids) {
        Map<Long, Existing> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        SyncTableSql sql = sqlByTable.get(table);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.selectExistingSql(), Map.of("ids", ids));
        for (Map<String, Object> row : rows) {
            long id = asLong(row.get("id"));
            result.put(id, new Existing(asLong(row.get("user_id")), asLong(row.get("version"))));
        }
        return result;
    }

    @Override
    public List<Row> selectChanges(SyncTable table, Collection<Long> userIds, LocalDateTime since, long sinceId, int limit) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("selectChanges 的可见账号集合不能为空（至少应含当前用户）");
        }
        SyncTableSql sql = sqlByTable.get(table);
        Map<String, Object> params = new HashMap<>(4);
        params.put("userIds", userIds);
        params.put("since", since);
        params.put("sinceId", sinceId);
        // LIMIT 用数值内联：limit 已在服务层钳到 [1, MAX]，不存在注入面，且部分驱动对 LIMIT ? 支持不一致
        List<Map<String, Object>> rows = jdbc.queryForList(sql.selectChangesSql() + " LIMIT " + limit, params);
        return rows.stream().map(row -> toRow(table, sql, row)).toList();
    }

    @Override
    public List<Row> selectBuiltinChanges(SyncTable table, LocalDateTime since, long sinceId, int limit) {
        SyncTableSql sql = sqlByTable.get(table);
        if (sql.selectBuiltinChangesSql() == null) {
            throw new IllegalArgumentException("表 " + table.tableName() + " 没有内置行（is_builtin 列），不支持基础数据通道");
        }
        Map<String, Object> params = new HashMap<>(4);
        params.put("since", since);
        params.put("sinceId", sinceId);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.selectBuiltinChangesSql() + " LIMIT " + limit, params);
        return rows.stream().map(row -> toRow(table, sql, row)).toList();
    }

    @Override
    public List<BaseRow> selectBaseChanges(BaseDataTable table, LocalDateTime since, long sinceId, int limit) {
        BaseDataTableSql sql = sqlByBaseTable.get(table);
        Map<String, Object> params = new HashMap<>(4);
        params.put("since", since);
        params.put("sinceId", sinceId);
        // LIMIT 用数值内联：limit 已在服务层钳到 [1, MAX]，不存在注入面
        List<Map<String, Object>> rows = jdbc.queryForList(sql.selectChangesSql() + " LIMIT " + limit, params);
        return rows.stream().map(row -> toBaseRow(table, sql, row)).toList();
    }

    @Override
    public void upsert(SyncTable table, Map<String, Object> payload, long userId) {
        SyncTableSql sql = sqlByTable.get(table);
        Map<String, Object> params = new HashMap<>(payload.size() + 8);
        params.put("id", payload.get("id"));
        for (String property : sql.payloadProperties()) {
            params.put(property, payload.get(property));
        }
        // deleted 缺省按未删除处理：NOT NULL 列显式写 null 会被库拒绝，而客户端漏传不该让整批失败
        Object deleted = payload.get("deleted");
        params.put("deleted", deleted == null ? 0 : deleted);
        if (sql.payloadProperties().contains("version")) {
            params.put("version", payload.get("version"));
        }
        params.put("userId", userId);
        if (sql.stampedProperties().contains("ownerUserId")) {
            params.put("ownerUserId", userId);
        }
        params.put("actorId", userId);
        params.put("now", LocalDateTime.now());
        jdbc.update(sql.insertSql(), params);
    }

    // ------------------------------------------------------------------

    private static Row toRow(SyncTable table, SyncTableSql sql, Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String property : sql.pullProperties()) {
            data.put(property, row.get(sql.columnOf(property)));
        }
        return new Row(table, asLong(row.get(sql.columnOf("id"))), cursorOf(row.get("server_updated_at")), data);
    }

    private static BaseRow toBaseRow(BaseDataTable table, BaseDataTableSql sql, Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String property : sql.pullProperties()) {
            data.put(property, row.get(sql.columnOf(property)));
        }
        return new BaseRow(table, asLong(row.get(sql.columnOf("id"))), cursorOf(row.get("server_updated_at")), data);
    }

    /**
     * 游标字段类型因驱动而异的兜底：Connector/J 8 的 {@code getObject} 对 DATETIME 返回
     * {@link LocalDateTime}，个别配置/旧驱动返回 {@link Timestamp}。
     */
    private static long cursorOf(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return SyncCursor.toMillis(localDateTime);
        }
        if (value instanceof Timestamp timestamp) {
            return SyncCursor.toMillis(timestamp.toLocalDateTime());
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
