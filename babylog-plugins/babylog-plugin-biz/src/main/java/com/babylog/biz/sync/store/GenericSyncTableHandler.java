package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.babylog.biz.entity.BlSyncDO;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.biz.sync.support.SyncTable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link SyncTableHandler} 的通用类型化实现（PivotOS 严格整改 S3）。
 *
 * <p>绑定一张同步表 + 它的视图实体（无 @TableLogic 的同步通道实体，见
 * {@code com.babylog.biz.sync.entity} 包）+ 视图 Mapper。所有查询/更新条件用
 * {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper} 表达；列引用全部是
 * {@link SFunction}（按属性名经 {@link SyncLambdas} 从实体 getter 纺出，
 * 非字符串列名）；分页用 MP 类型化 {@link Page}（searchCount=false，无 COUNT 开销），
 * 全类无 {@code last()} 片段/{@code apply}/任何 SQL 文本。
 */
public class GenericSyncTableHandler<E extends BlSyncDO> implements SyncTableHandler {

    private final SyncTable table;
    private final BaseMapper<E> mapper;
    private final Class<E> viewClass;
    private final SyncEntityMeta meta;
    private final SFunction<E, ?> pullPredicateColumn;
    private final Object pullPredicateValue;
    private final SFunction<E, ?> builtinColumn;
    private final Set<String> rejectNullProperties;

    public GenericSyncTableHandler(SyncTable table, BaseMapper<E> mapper, Class<E> viewClass,
                                   SyncViewRegistry.SyncSpec spec) {
        this.table = Objects.requireNonNull(table, "table");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.viewClass = Objects.requireNonNull(viewClass, "viewClass");
        if (spec == null || spec.table() != table) {
            throw new IllegalArgumentException("注册规格与表不匹配：" + table.apiName());
        }
        this.meta = SyncEntityMeta.from(viewClass, table.serverOwnedProperties());
        this.pullPredicateColumn = spec.predicateProperty() == null
                ? null : SyncLambdas.resolve(viewClass, spec.predicateProperty());
        this.pullPredicateValue = spec.predicateValue();
        this.builtinColumn = SyncViewRegistry.builtinProperty(table) == null
                ? null : SyncLambdas.resolve(viewClass, SyncViewRegistry.builtinProperty(table));
        this.rejectNullProperties = spec.rejectNullProperties();
    }

    @Override
    public SyncTable table() {
        return table;
    }

    @Override
    public Map<Long, SyncRecordStore.Existing> selectExisting(Collection<Long> ids) {
        Map<Long, SyncRecordStore.Existing> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        // 视图实体无 @TableLogic：deleted=1 的行天然可见（软删除墓碑同样要参与版本裁决）
        List<E> rows = mapper.selectList(new LambdaQueryWrapper<E>()
                .select(column("id"), column("userId"), column("version"))
                .in(column("id"), ids));
        for (E row : rows) {
            long id = asLong(SyncPropertyAccess.read(row, "id"));
            result.put(id, new SyncRecordStore.Existing(
                    asLong(SyncPropertyAccess.read(row, "userId")),
                    asLong(SyncPropertyAccess.read(row, "version"))));
        }
        return result;
    }

    @Override
    public List<SyncRecordStore.Row> selectChanges(Collection<Long> userIds, LocalDateTime since,
                                                   long sinceId, int limit) {
        return page(cursorWrapper(userIds, since, sinceId), limit).stream()
                .map(this::toRow).toList();
    }

    @Override
    public List<SyncRecordStore.Row> selectBuiltinChanges(LocalDateTime since, long sinceId, int limit) {
        if (builtinColumn == null) {
            throw new IllegalArgumentException("表 " + table.tableName()
                    + " 没有内置行（is_builtin 列），不支持基础数据通道");
        }
        LambdaQueryWrapper<E> wrapper = cursorWrapper(null, since, sinceId)
                .eq(builtinColumn, 1);
        return page(wrapper, limit).stream().map(this::toRow).toList();
    }

    @Override
    public void upsert(Map<String, Object> payload, long userId) {
        long id = asLong(payload.get("id"));
        long version = asLong(payload.get("version"));
        if (id <= 0 || version < 1) {
            // 服务层已门禁（id>0、version≥1），这里是存储层的防御性 fail-fast
            throw new IllegalArgumentException("upsert 载荷缺少合法的 id/version：table=" + table.apiName());
        }
        E entity = newEntity();
        entity.setId(id);
        // 载荷 → 实体：只取该表登记过的载荷列（对象层赋值，与 SQL 无关）
        for (String property : meta.payloadProperties()) {
            Object value = payload.get(property);
            if (value == null && rejectNullProperties.contains(property)) {
                // 对齐旧 ODKU 行为：NOT NULL（带默认值）列收到显式 null 时由库拒绝，
                // 上层 push 据此走 8342 单条拒收——不接受「静默落默认值」的语义漂移
                throw new DataIntegrityViolationException("字段不满足库约束：" + property
                        + " 不能为 null（" + table.tableName() + " 该列 NOT NULL）");
            }
            SyncPropertyAccess.write(entity, property, value);
        }
        if (SyncPropertyAccess.read(entity, "deleted") == null) {
            // deleted 缺省按未删除处理：客户端漏传不该让整批失败（与旧实现同口径）
            SyncPropertyAccess.write(entity, "deleted", 0);
        }
        // 归属盖章：user_id 恒按 token 写入；owner_user_id 只在该表真有此列时盖章
        entity.setUserId(userId);
        if (meta.hasProperty("ownerUserId")) {
            SyncPropertyAccess.write(entity, "ownerUserId", userId);
        }
        LocalDateTime now = LocalDateTime.now();
        SyncPropertyAccess.write(entity, "createBy", userId);
        SyncPropertyAccess.write(entity, "createTime", now);
        SyncPropertyAccess.write(entity, "updateBy", userId);
        SyncPropertyAccess.write(entity, "updateTime", now);

        // 1) 版本门禁原子更新（替代 ODKU 的 IF(:version > version, …) 逐列裁决）
        if (versionGatedUpdate(entity, version, now) > 0) {
            return;
        }
        // 2) 行不存在 → 插入（归属与审计列只在此刻写入）
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 3) 唯一键竞争：并发同 id 首插，一方已落库 → 交回版本门禁裁决
            if (versionGatedUpdate(entity, version, now) == 0) {
                // 对方已写入不旧于本方的版本：last-write-wins 输掉，幂等跳过
                // （与服务层 skipped 同语义，不抛错——客户端重推安全）
            }
        }
    }

    @Override
    public int closeOtherOngoingFeedings(long userId, long childId, String type, long keepId) {
        if (table != SyncTable.FEEDINGS || type == null || type.isBlank()) {
            return 0;
        }
        // 类型化条件（零手写 SQL）：只动自己的行、只动进行中且未删除的行、保留刚落库的那条。
        // deleted 置 1 后 server_updated_at 由库自动刷新 → 各端增量拉取拿到这条墓碑。
        LambdaUpdateWrapper<E> wrapper = new LambdaUpdateWrapper<E>()
                .eq(column("userId"), userId)
                .eq(column("childId"), childId)
                .eq(column("type"), type)
                .eq(column("inProgress"), 1)
                .eq(column("deleted"), 0)
                .ne(column("id"), keepId)
                .set(column("deleted"), 1);
        return mapper.update(null, wrapper);
    }

    // ------------------------------------------------------------------
    // 以下全部是类型化条件拼装与对象映射，无任何 SQL 文本
    // ------------------------------------------------------------------

    /** 双段游标条件（归属过滤可选——内置行通道传 null）+ 稳定排序；分页与谓词由调用方叠加 */
    private LambdaQueryWrapper<E> cursorWrapper(Collection<Long> userIds, LocalDateTime since, long sinceId) {
        LambdaQueryWrapper<E> wrapper = new LambdaQueryWrapper<>();
        if (userIds != null) {
            wrapper.in(column("userId"), userIds);
        }
        if (pullPredicateColumn != null && userIds != null) {
            // 业务谓词（M37 feedings in_progress = 0）：类型化等值条件
            wrapper.eq(pullPredicateColumn, pullPredicateValue);
        }
        wrapper.and(w -> w.gt(column("serverUpdatedAt"), since)
                .or(o -> o.eq(column("serverUpdatedAt"), since).gt(column("id"), sinceId)));
        wrapper.orderByAsc(column("serverUpdatedAt")).orderByAsc(column("id"));
        return wrapper;
    }

    /** MP 类型化分页：searchCount=false（增量拉取不需要 COUNT），LIMIT 由分页插件生成 */
    private List<E> page(LambdaQueryWrapper<E> wrapper, int limit) {
        return mapper.selectPage(new Page<>(1, limit, false), wrapper).getRecords();
    }

    private SyncRecordStore.Row toRow(E entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String property : meta.pullProperties()) {
            data.put(property, SyncPropertyAccess.read(entity, property));
        }
        return new SyncRecordStore.Row(table, entity.getId(),
                SyncCursor.toMillis(entity.getServerUpdatedAt()), data);
    }

    /**
     * 版本门禁条件更新：{@code SET <载荷列>=?, update_by=?, update_time=?, version=? WHERE id=? AND version<?}。
     * 单行原子裁决——并发推送谁先谁后都不会出现旧版本覆盖新版本；
     * 归属列（user_id/owner_user_id）刻意不在 SET 里：更新路径永不改写归属（与旧 ODKU 同）。
     */
    private int versionGatedUpdate(E entity, long payloadVersion, LocalDateTime now) {
        LambdaUpdateWrapper<E> wrapper = new LambdaUpdateWrapper<>();
        for (String property : meta.payloadProperties()) {
            if ("version".equals(property)) {
                continue;
            }
            wrapper.set(column(property), SyncPropertyAccess.read(entity, property));
        }
        wrapper.set(column("updateBy"), SyncPropertyAccess.read(entity, "updateBy"));
        wrapper.set(column("updateTime"), now);
        wrapper.set(column("version"), payloadVersion);
        wrapper.eq(column("id"), entity.getId());
        wrapper.lt(column("version"), payloadVersion);
        return mapper.update(null, wrapper);
    }

    private SFunction<E, ?> column(String property) {
        return SyncLambdas.resolve(viewClass, property);
    }

    private E newEntity() {
        try {
            return viewClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("实例化视图实体失败：" + viewClass.getName(), e);
        }
    }

    private static long asLong(Object value) {
        // 与服务层 SyncServiceImpl.asLong 同口径：JSON 载荷里 id/version 常以字符串形式
        // 到达（Jackson 泛型 Map 不自动收窄），真实链路上必须容忍字符串数字
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
