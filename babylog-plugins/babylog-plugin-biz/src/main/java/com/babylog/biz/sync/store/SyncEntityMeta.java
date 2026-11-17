package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.annotation.TableId;

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
 * 同步视图实体的属性清单推导：<b>纯实体元数据，零 SQL</b>（PivotOS 严格整改 S3）。
 *
 * <p>列清单的唯一真相是实体字段（改列只改实体 + 迁移）。这里只做对象层的分类，
 * SQL 由 MyBatis-Plus 依据这些清单与 {@code LambdaQueryWrapper} 生成。
 * 口径自 {@code SyncTableSql}（S2 前身的正确部分）原样继承：
 *
 * <ul>
 *   <li><b>父类字段优先、按属性名去重</b>：字段遮蔽（如 BlFeeding.createdBy 遮蔽
 *       BaseDO.createBy）必须按父类口径裁决——遮蔽字段不进出同步协议；</li>
 *   <li>列分四类：<b>主键</b> id（单独处理，拉取排最前）；
 *       <b>载荷列</b>（{@link #payloadProperties}：业务列 + version + deleted，客户端可推）；
 *       <b>归属列</b>（{@link #stampedProperties}：userId / ownerUserId，服务端盖章、忽略客户端传值）；
 *       <b>审计列</b>（createBy/createTime/updateBy/updateTime + serverUpdatedAt，服务端独占）；</li>
 *   <li>{@link SyncTable#serverOwnedProperties()}（如 children.familyId）与全局
 *       {@link #SERVER_OWNED} 一样，既不进载荷也不回传。</li>
 * </ul>
 */
final class SyncEntityMeta {

    /** 服务端独占属性（全局）：归属、游标与审计，客户端伪造无效 */
    private static final Set<String> SERVER_OWNED = Set.of(
            "userId", "serverUpdatedAt", "createBy", "createTime", "updateBy", "updateTime");

    /** 归属盖章属性：只在该表真有这一列时生效 */
    private static final Set<String> STAMPED = Set.of("userId", "ownerUserId");

    private final List<String> payloadProperties;
    private final List<String> pullProperties;
    private final List<String> stampedProperties;
    private final Set<String> declaredProperties;

    private SyncEntityMeta(List<String> payloadProperties, List<String> pullProperties,
                           List<String> stampedProperties, Set<String> declaredProperties) {
        this.payloadProperties = payloadProperties;
        this.pullProperties = pullProperties;
        this.stampedProperties = stampedProperties;
        this.declaredProperties = declaredProperties;
    }

    /**
     * 从视图实体类推导属性清单。
     *
     * @param viewClass         同步通道视图实体（遮蔽了 deleted/version 等基类声明的本类）
     * @param serverOwnedByTable 该表额外的服务端独占属性（SyncTable.serverOwnedProperties）
     */
    static SyncEntityMeta from(Class<?> viewClass, Set<String> serverOwnedByTable) {
        List<String> payload = new ArrayList<>();
        List<String> stamped = new ArrayList<>();
        List<String> pullProps = new ArrayList<>();
        Set<String> declared = new HashSet<>();

        for (Field field : collectFields(viewClass)) {
            String property = field.getName();
            declared.add(property);
            boolean pk = field.getAnnotation(TableId.class) != null;
            if (pk) {
                continue;
            }
            boolean serverOwned = SERVER_OWNED.contains(property) || serverOwnedByTable.contains(property);
            if (!serverOwned) {
                pullProps.add(property);
            }
            if (STAMPED.contains(property)) {
                stamped.add(property);
                continue;
            }
            if (!serverOwned) {
                payload.add(property);
            }
        }
        // 主键单独处理（上面 continue 掉了），拉取时排在最前，客户端一眼能对上
        pullProps.add(0, "id");
        return new SyncEntityMeta(List.copyOf(payload), List.copyOf(pullProps),
                List.copyOf(stamped), Set.copyOf(declared));
    }

    /** 客户端可推的属性（camelCase，与 JSON 字段同名；含 version/deleted） */
    List<String> payloadProperties() {
        return payloadProperties;
    }

    /** 归属盖章属性（userId / ownerUserId；只在该表真有这一列时出现） */
    List<String> stampedProperties() {
        return stampedProperties;
    }

    /** 拉取返回的属性（camelCase，含 id/ownerUserId/version/deleted，不含归属与审计列） */
    List<String> pullProperties() {
        return pullProperties;
    }

    /** 该实体是否声明了某属性（如 ownerUserId / isBuiltin / inProgress） */
    boolean hasProperty(String property) {
        return declaredProperties.contains(property);
    }

    /**
     * 父类字段在前（BaseDO → BlSyncDO → 实体），按<b>属性名去重</b>：
     * 字段遮蔽时父类的那个胜出（它是 @TableId / 归属 / 审计列的声明处），
     * 子类遮蔽字段在实体层面就应当消灭，这里只是兜底（与 SyncTableSql 同款口径）。
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
}
