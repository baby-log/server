package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.babylog.biz.sync.support.SyncTable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一张同步表的 SQL 骨架（列清单 + 三条语句），由实体字段反射生成，进程内只建一次。
 *
 * <p><b>为什么反射生成而不是手写 18 张表的 SQL</b>：
 * 18 表 × 约 20 列，手写 insert/upsert/select 三套语句是 1000+ 行极易错位的代码
 * （M5 就栽过一次 JdbcTemplate 列顺序与参数顺序错位：注册成功、登录恒 2001，
 * 且被 Mock 的单测永远发现不了）。列名的唯一真相是实体字段 + {@code @TableField}，
 * 这里把它读出来一次，改列只改实体与迁移脚本，SQL 自动跟上。
 *
 * <p><b>为什么必须绕过 MyBatis-Plus 的 {@code @TableLogic}</b>：
 * 同步表的软删除是<b>同步信号</b>（deleted=1 的行也要能被覆盖、也要被拉到客户端），
 * 而 MP 的 {@code selectById}/{@code updateById} 会自动追加 {@code deleted = 0}，
 * 导致「客户端先软删、再改同一条」时永远命中不了库内那行（插入撞主键、更新 0 行）。
 * 因此同步通道用 {@link org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate}
 * 直写列清单——这是<b>本插件自己的 bl_ 表</b>，不涉及跨插件表结构耦合。
 *
 * <p>列分四类：
 * <ul>
 *   <li><b>主键</b>：id（客户端生成，服务端不改）；</li>
 *   <li><b>载荷列</b>（{@link #payloadProperties}）：业务列 + version + deleted，客户端可推；</li>
 *   <li><b>归属列</b>（{@link #stampedProperties}）：user_id / owner_user_id，服务端按 token 盖章，
 *       <b>忽略客户端传值</b>；</li>
 *   <li><b>审计列</b>：create_by/create_time/update_by/update_time，服务端自己写。</li>
 * </ul>
 */
final class SyncTableSql {

    /** 服务端独占列：既不出现在载荷里，也不回传给客户端 */
    private static final Set<String> SERVER_OWNED = Set.of(
            "userId", "serverUpdatedAt", "createBy", "createTime", "updateBy", "updateTime");

    /** 归属盖章列：只在 INSERT 时写当前用户，更新时不动（避免把别人的行改成自己的） */
    private static final Set<String> STAMPED = Set.of("userId", "ownerUserId");

    private final SyncTable table;
    private final String idColumn;
    private final List<String> payloadProperties;
    private final List<String> stampedProperties;
    private final Map<String, String> propertyToColumn;
    private final List<String> pullColumns;
    private final List<String> pullProperties;
    private final String insertSql;
    private final String selectExistingSql;
    private final String selectChangesSql;
    private final String selectBuiltinChangesSql;

    SyncTableSql(SyncTable table) {
        this.table = table;
        this.propertyToColumn = new LinkedHashMap<>();

        Class<?> entityClass = table.entityClass();
        String pk = "id";
        List<String> payload = new ArrayList<>();
        List<String> stamped = new ArrayList<>();
        List<String> pullProps = new ArrayList<>();

        for (Field field : collectFields(entityClass)) {
            String property = field.getName();
            String column = resolveColumn(field, property);
            propertyToColumn.put(property, column);
            TableId tableId = field.getAnnotation(TableId.class);
            if (tableId != null) {
                pk = column;
                continue;
            }
            if (!isServerOwned(property)) {
                pullProps.add(property);
            }
            if (STAMPED.contains(property)) {
                stamped.add(property);
                continue;
            }
            if (!isServerOwned(property)) {
                payload.add(property);
            }
        }

        this.idColumn = pk;
        this.payloadProperties = List.copyOf(payload);
        this.stampedProperties = List.copyOf(stamped);
        // 主键单独处理（上面 continue 掉了），拉取时排在最前，客户端一眼能对上
        pullProps.add(0, "id");
        this.pullProperties = List.copyOf(pullProps);
        this.pullColumns = pullProps.stream().map(propertyToColumn::get).toList();
        this.insertSql = buildInsertSql();
        this.selectExistingSql = "SELECT " + pk + ", user_id, version FROM " + table.tableName()
                + " WHERE " + pk + " IN (:ids)";
        this.selectChangesSql = buildSelectChangesSql();
        // 内置行下发语句（M8 基础数据通道）：只对带 is_builtin 列的表生成，其余表为 null
        this.selectBuiltinChangesSql = hasBuiltinFlag()
                ? buildSelectBuiltinChangesSql() : null;
    }

    SyncTable table() {
        return table;
    }

    String idColumn() {
        return idColumn;
    }

    /** 客户端可推的属性（camelCase，与 JSON 字段同名） */
    List<String> payloadProperties() {
        return payloadProperties;
    }

    /** 归属盖章属性（userId / ownerUserId；只在该表真有这一列时出现） */
    List<String> stampedProperties() {
        return stampedProperties;
    }

    /** 拉取返回的属性（camelCase，含 ownerUserId/version/deleted，不含归属与审计列） */
    List<String> pullProperties() {
        return pullProperties;
    }

    String selectExistingSql() {
        return selectExistingSql;
    }

    String selectChangesSql() {
        return selectChangesSql;
    }

    /**
     * 内置行增量下发语句（{@code WHERE is_builtin = 1}，不按 user_id 过滤）。
     *
     * <p>只对带 {@code is_builtin} 列的表（当前仅 products）生成，其余表返回 {@code null}，
     * 调用方据此拒绝（而不是静默查一张没有该列的表报 SQL 错误）。
     */
    String selectBuiltinChangesSql() {
        return selectBuiltinChangesSql;
    }

    String insertSql() {
        return insertSql;
    }

    String columnOf(String property) {
        return propertyToColumn.get(property);
    }

    // ------------------------------------------------------------------

    /**
     * INSERT … ON DUPLICATE KEY UPDATE：仅当推送 version &gt; 库内 version 才覆盖。
     *
     * <p><b>赋值顺序有两个坑</b>：
     * <ol>
     *   <li>MySQL 的 ODKU 赋值是<b>从左到右</b>生效的，后面读到的已经是改过的值。
     *       所以 {@code version} 必须放在<b>最后</b>——先改 version 会让前面所有
     *       {@code IF(:version > version, …)} 全部判假，等于什么都没更新。</li>
     *   <li>审计列 {@code update_by/update_time} 不参与版本门禁（「谁在什么时候推过」
     *       是服务端事实，与业务内容是否被覆盖无关）。</li>
     * </ol>
     */
    private String buildInsertSql() {
        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();
        columns.add(idColumn);
        values.add(":id");

        for (String property : payloadProperties) {
            columns.add(propertyToColumn.get(property));
            values.add(":" + property);
        }
        for (String property : stampedProperties) {
            columns.add(propertyToColumn.get(property));
            values.add(":" + property);
        }
        columns.add("create_by");
        values.add(":actorId");
        columns.add("create_time");
        values.add(":now");
        columns.add("update_by");
        values.add(":actorId");
        columns.add("update_time");
        values.add(":now");

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table.tableName())
                .append(" (").append(String.join(", ", columns)).append(")")
                .append(" VALUES (").append(String.join(", ", values)).append(")")
                .append(" ON DUPLICATE KEY UPDATE ");
        List<String> assignments = new ArrayList<>();
        for (String property : payloadProperties) {
            if ("version".equals(property)) {
                continue;
            }
            String column = propertyToColumn.get(property);
            assignments.add(column + " = IF(:version > version, :" + property + ", " + column + ")");
        }
        assignments.add("update_by = :actorId");
        assignments.add("update_time = :now");
        assignments.add("version = IF(:version > version, :version, version)");
        return sql.append(String.join(", ", assignments)).toString();
    }

    /**
     * 游标增量拉取语句。
     *
     * <p>游标是<b>双段</b>的：{@code server_updated_at} + {@code id}。
     * 单靠时间戳会在「同一毫秒写入的行数 &gt; 单页上限」时丢数据
     * （MySQL 的 NOW(3) 在同一条语句内是常量，一次 push 的 200 行可能共用一个毫秒值），
     * 加 id 做同毫秒 tiebreak 后，「下一页」从上一页最后一条的 id 之后继续。
     * 客户端不感知这条细节，只需把 nextSince/nextSinceId 原样回传。
     *
     * <p><b>可见范围是账号集合</b>（V2.1 M15）：无家庭用户集合里只有自己（行为与 V2.0 的
     * {@code user_id = :userId} 完全一致）；家庭成员的集合 = 家庭全部 ACTIVE 成员
     * （读侧家庭可见，01B 第六节）。集合由 {@code SyncAccessScopeResolver} 在服务层
     * 推导后传入，存储层不做任何权限判断。
     */
    private String buildSelectChangesSql() {
        return "SELECT " + String.join(", ", pullColumns) + ", server_updated_at"
                + " FROM " + table.tableName()
                + " WHERE user_id IN (:userIds)"
                + " AND (server_updated_at > :since OR (server_updated_at = :since AND " + idColumn + " > :sinceId))"
                + " ORDER BY server_updated_at, " + idColumn;
    }

    /**
     * 内置行增量下发：与 {@link #buildSelectChangesSql()} 同构，但归属条件换成
     * {@code is_builtin = 1}——内置行是全局基础数据（user_id=0 的占位），任何登录用户
     * 都该拉到同一份。游标仍是双段（后台批量改内置行时同样存在同毫秒多行）。
     */
    private String buildSelectBuiltinChangesSql() {
        return "SELECT " + String.join(", ", pullColumns) + ", server_updated_at"
                + " FROM " + table.tableName()
                + " WHERE is_builtin = 1"
                + " AND (server_updated_at > :since OR (server_updated_at = :since AND " + idColumn + " > :sinceId))"
                + " ORDER BY server_updated_at, " + idColumn;
    }

    /** 该表实体是否声明了 isBuiltin 字段（即库表有 is_builtin 列） */
    private boolean hasBuiltinFlag() {
        return propertyToColumn.containsKey("isBuiltin");
    }

    /** 服务端独占：全局列 + 本表登记列（如 bl_children.family_id）都不进载荷与拉取 */
    private boolean isServerOwned(String property) {
        return SERVER_OWNED.contains(property) || table.serverOwnedProperties().contains(property);
    }

    // ------------------------------------------------------------------

    /**
     * 父类字段在前（BaseDO → BlSyncDO → 实体），保证列顺序稳定可读。
     *
     * <p>按<b>属性名去重</b>：字段遮蔽（子类重复声明父类同名字段）会让同一列被推导两次，
     * 生成的 insert 语句直接是 {@code Duplicate column} ——父类的那个胜出（它是 @Version /
     * 归属列的声明处），子类的遮蔽字段在实体层面就应当消灭，这里只是兜底。
     */
    private static List<Field> collectFields(Class<?> type) {
        Deque<Class<?>> chain = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            chain.addFirst(current);
        }
        List<Field> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> current : chain) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic()
                        || Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                if (!seen.add(field.getName())) {
                    continue;
                }
                fields.add(field);
            }
        }
        return fields;
    }

    private static String resolveColumn(Field field, String property) {
        TableField tableField = field.getAnnotation(TableField.class);
        if (tableField != null && !tableField.value().isBlank()) {
            return tableField.value();
        }
        return camelToSnake(property);
    }

    /** camelCase → snake_case（amountMl → amount_ml、serverUpdatedAt → server_updated_at） */
    static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
