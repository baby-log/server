package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncTable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 同步记录存储的 MyBatis-Plus 类型化实现（PivotOS 严格整改 S3）。
 *
 * <p>取代 S2 前的 {@code JdbcSyncRecordStore}（NamedParameterJdbcTemplate + 反射拼 SQL）。
 * 每张表一个 {@link SyncTableHandler}/{@link BaseTableHandler}（LambdaQueryWrapper/
 * LambdaUpdateWrapper，零手写 SQL），本类只做路由、limit 钳位与注册完整性校验。
 *
 * <p>对外契约与旧实现逐条一致：Existing/Row/BaseRow 形态、双段游标语义、
 * limit 边界 [1,1000]、空账号集合抛 IllegalArgumentException、
 * 无 is_builtin 的表走内置通道抛 IllegalArgumentException。
 */
@Repository
public class MybatisPlusSyncRecordStore implements SyncRecordStore {

    /** 与旧 withLimit 同款边界：服务层钳到 [1,500]，这里是最后防线 */
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 1000;

    private final Map<SyncTable, SyncTableHandler> syncHandlers;
    private final Map<BaseDataTable, BaseTableHandler> baseHandlers;

    public MybatisPlusSyncRecordStore(List<SyncTableHandler> syncHandlers, List<BaseTableHandler> baseHandlers) {
        this.syncHandlers = new EnumMap<>(SyncTable.class);
        for (SyncTableHandler handler : syncHandlers) {
            if (handler == null) {
                continue;
            }
            SyncTableHandler prev = this.syncHandlers.put(handler.table(), handler);
            if (prev != null) {
                throw new IllegalStateException("同步表 " + handler.table().apiName() + " 重复注册 Handler");
            }
        }
        this.baseHandlers = new EnumMap<>(BaseDataTable.class);
        for (BaseTableHandler handler : baseHandlers) {
            if (handler == null) {
                continue;
            }
            BaseTableHandler prev = this.baseHandlers.put(handler.table(), handler);
            if (prev != null) {
                throw new IllegalStateException("base 表 " + handler.table().apiName() + " 重复注册 Handler");
            }
        }
        // 注册完整性 fail-fast：任何一张表缺 Handler 都是「静默回退动态 SQL」的口子，启动即炸
        for (SyncTable table : SyncTable.values()) {
            if (!this.syncHandlers.containsKey(table)) {
                throw new IllegalStateException("同步表 " + table.apiName() + " 未注册类型化 Handler");
            }
        }
        for (BaseDataTable table : BaseDataTable.values()) {
            if (!this.baseHandlers.containsKey(table)) {
                throw new IllegalStateException("base 表 " + table.apiName() + " 未注册类型化 Handler");
            }
        }
    }

    @Override
    public Map<Long, Existing> selectExisting(SyncTable table, Collection<Long> ids) {
        return syncHandlers.get(table).selectExisting(ids);
    }

    @Override
    public List<Row> selectChanges(SyncTable table, Collection<Long> userIds,
                                   LocalDateTime since, long sinceId, int limit) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("selectChanges 的可见账号集合不能为空（至少应含当前用户）");
        }
        return syncHandlers.get(table).selectChanges(userIds, since, sinceId, checkLimit(limit));
    }

    @Override
    public List<Row> selectBuiltinChanges(SyncTable table, LocalDateTime since, long sinceId, int limit) {
        return syncHandlers.get(table).selectBuiltinChanges(since, sinceId, checkLimit(limit));
    }

    @Override
    public List<BaseRow> selectBaseChanges(BaseDataTable table, LocalDateTime since, long sinceId, int limit) {
        return baseHandlers.get(table).selectChanges(since, sinceId, checkLimit(limit));
    }

    @Override
    public void upsert(SyncTable table, Map<String, Object> payload, long userId) {
        syncHandlers.get(table).upsert(payload, userId);
    }

    private static int checkLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit 越界（须在 [1, 1000] 内，服务层钳位失效？）：" + limit);
        }
        return limit;
    }
}
