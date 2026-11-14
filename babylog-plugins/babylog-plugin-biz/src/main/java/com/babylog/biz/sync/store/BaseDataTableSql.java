package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.BaseDataTable;

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
 * 一张基础数据运营表的拉取 SQL 骨架（M40），列清单由实体字段反射生成。
 *
 * <p>与 {@link SyncTableSql} 同构但简单得多：base 通道是<b>单向下发</b>，没有
 * push 载荷/归属盖章/版本门禁，只有一条增量拉取语句。列规则：
 * <ul>
 *   <li>主键 id 排最前（客户端对账第一眼）；</li>
 *   <li>审计四件套（createBy/createTime/updateBy/updateTime）不下发；</li>
 *   <li>{@code deleted} 随行下发（运营表的软删除同样是客户端要应用的事实）；</li>
 *   <li>{@code server_updated_at} 只做游标不进载荷（实体本就不声明该字段，V2.2.12）。</li>
 * </ul>
 */
final class BaseDataTableSql {

    /** 不下发的服务端列（审计四件套 + 游标列兜底） */
    private static final Set<String> SERVER_OWNED = Set.of(
            "serverUpdatedAt", "createBy", "createTime", "updateBy", "updateTime");

    private final BaseDataTable table;
    private final List<String> pullProperties;
    private final Map<String, String> propertyToColumn;
    private final String selectChangesSql;

    BaseDataTableSql(BaseDataTable table) {
        this.table = table;
        this.propertyToColumn = new LinkedHashMap<>();
        List<String> pullProps = new ArrayList<>();

        for (Field field : collectFields(table.entityClass())) {
            String property = field.getName();
            propertyToColumn.put(property, resolveColumn(field, property));
            if ("id".equals(property)) {
                // 主键排最前（continue 掉），客户端一眼对得上号
                continue;
            }
            if (!SERVER_OWNED.contains(property)) {
                pullProps.add(property);
            }
        }
        pullProps.add(0, "id");
        this.pullProperties = List.copyOf(pullProps);
        List<String> pullColumns = pullProps.stream().map(propertyToColumn::get).toList();

        String predicate = table.pullPredicate();
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", pullColumns))
                .append(", server_updated_at FROM ").append(table.tableName());
        if (!predicate.isEmpty()) {
            sql.append(" WHERE ").append(predicate);
        }
        String cursorCondition = "(server_updated_at > :since OR (server_updated_at = :since AND "
                + propertyToColumn.get("id") + " > :sinceId))";
        sql.append(predicate.isEmpty() ? " WHERE " : " AND ").append(cursorCondition)
                .append(" ORDER BY server_updated_at, ").append(propertyToColumn.get("id"));
        this.selectChangesSql = sql.toString();
    }

    BaseDataTable table() {
        return table;
    }

    /** 拉取返回的属性（camelCase，含 id/deleted，不含审计列与游标列） */
    List<String> pullProperties() {
        return pullProperties;
    }

    String selectChangesSql() {
        return selectChangesSql;
    }

    String columnOf(String property) {
        return propertyToColumn.get(property);
    }

    /** 父类字段在前、按属性名去重（与 SyncTableSql#collectFields 同款兜底） */
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
        com.baomidou.mybatisplus.annotation.TableField tableField =
                field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        if (tableField != null && !tableField.value().isBlank()) {
            return tableField.value();
        }
        return SyncTableSql.camelToSnake(property);
    }
}
