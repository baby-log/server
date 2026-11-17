package com.babylog.biz.sync.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncCursor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link BaseTableHandler} 的通用类型化实现（PivotOS 严格整改 S3，M40 通道）。
 *
 * <p>绑定一张运营表 + 无 @TableLogic 的视图实体（软删除事实随 deleted 列原样下发）。
 * 条件/排序/分页全部类型化；业务谓词（当前仅版本表 status = PUBLISHED）由注册规格
 * 以 {@link SFunction} 等值条件承担——谓词不再以 SQL 片段形式存在。
 */
public class GenericBaseTableHandler<E> implements BaseTableHandler {

    private final BaseDataTable table;
    private final BaseMapper<E> mapper;
    private final Class<E> viewClass;
    private final SyncEntityMeta meta;
    private final SFunction<E, ?> predicateColumn;
    private final Object predicateValue;

    public GenericBaseTableHandler(BaseDataTable table, BaseMapper<E> mapper, Class<E> viewClass,
                                   SyncViewRegistry.BaseSpec spec) {
        this.table = Objects.requireNonNull(table, "table");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.viewClass = Objects.requireNonNull(viewClass, "viewClass");
        if (spec == null || spec.table() != table) {
            throw new IllegalArgumentException("注册规格与表不匹配：" + table.apiName());
        }
        this.meta = SyncEntityMeta.from(viewClass, java.util.Set.of());
        this.predicateColumn = spec.predicateProperty() == null
                ? null : SyncLambdas.resolve(viewClass, spec.predicateProperty());
        this.predicateValue = spec.predicateValue();
    }

    @Override
    public BaseDataTable table() {
        return table;
    }

    @Override
    public List<SyncRecordStore.BaseRow> selectChanges(LocalDateTime since, long sinceId, int limit) {
        LambdaQueryWrapper<E> wrapper = new LambdaQueryWrapper<>();
        if (predicateColumn != null) {
            wrapper.eq(predicateColumn, predicateValue);
        }
        wrapper.and(w -> w.gt(col("serverUpdatedAt"), since)
                .or(o -> o.eq(col("serverUpdatedAt"), since).gt(col("id"), sinceId)));
        wrapper.orderByAsc(col("serverUpdatedAt")).orderByAsc(col("id"));
        return mapper.selectPage(new Page<>(1, limit, false), wrapper).getRecords()
                .stream().map(this::toRow).toList();
    }

    private SyncRecordStore.BaseRow toRow(E entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String property : meta.pullProperties()) {
            data.put(property, SyncPropertyAccess.read(entity, property));
        }
        Object id = SyncPropertyAccess.read(entity, "id");
        Object cursor = SyncPropertyAccess.read(entity, "serverUpdatedAt");
        return new SyncRecordStore.BaseRow(table, id instanceof Number n ? n.longValue() : 0L,
                cursor instanceof LocalDateTime t ? SyncCursor.toMillis(t) : 0L, data);
    }

    private SFunction<E, ?> col(String property) {
        return SyncLambdas.resolve(viewClass, property);
    }
}
