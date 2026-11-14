package com.babylog.biz.sync.service.impl;

import com.babylog.biz.sync.service.BaseDataService;
import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.biz.sync.support.SyncTable;
import com.babylog.biz.sync.vo.SyncChangesVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据下发实现（内置产品 + 运营表三类，M40 扩范围）。
 *
 * <p>游标逻辑与 {@code SyncServiceImpl#pull} 同构（双段、normalize、空页退回原游标），
 * 只是单表、无分类过滤、不过滤归属——归一化函数已下沉 {@link SyncCursor} 保证两通道一套口径。
 * App 端解析完全复用 {@code /sync/changes} 的代码路径，每类数据独立一对游标、互不干扰。
 */
@Service
public class BaseDataServiceImpl implements BaseDataService {

    /** 单页默认记录数：内置产品全量才 32 行，默认一页拉完 */
    static final int DEFAULT_PULL_LIMIT = 100;
    /** 单页上限：将来后台批量导入产品库时留余量 */
    static final int MAX_PULL_LIMIT = 500;
    /** 与同步通道共用同一容差（客户端误传手机时钟的纠正阈值） */
    private static final long CURSOR_FUTURE_TOLERANCE_MILLIS = 10 * 60 * 1000L;

    private final SyncRecordStore store;

    public BaseDataServiceImpl(SyncRecordStore store) {
        this.store = store;
    }

    @Override
    public SyncChangesVO pullBuiltinProducts(Long sinceMillis, Long sinceId, Integer limit) {
        int pageLimit = SyncCursor.normalizeLimit(limit, DEFAULT_PULL_LIMIT, MAX_PULL_LIMIT);
        long serverTime = SyncCursor.now();
        long effectiveSince = SyncCursor.normalizeSince(sinceMillis, serverTime, CURSOR_FUTURE_TOLERANCE_MILLIS);
        long effectiveSinceId = sinceId == null ? 0L : Math.max(0L, sinceId);

        List<SyncRecordStore.Row> rows = store.selectBuiltinChanges(
                SyncTable.PRODUCTS, SyncCursor.toLocalDateTime(effectiveSince), effectiveSinceId, pageLimit);
        // 单表无需跨表归并，SQL 已按 (server_updated_at, id) 排序
        boolean hasMore = rows.size() >= pageLimit;
        List<SyncRecordStore.Row> page = hasMore ? rows.subList(0, pageLimit) : rows;

        List<Map<String, Object>> items = new ArrayList<>(page.size());
        for (SyncRecordStore.Row row : page) {
            items.add(row.data());
        }
        SyncRecordStore.Row last = page.isEmpty() ? null : page.get(page.size() - 1);
        return buildVo(SyncTable.PRODUCTS.apiName(), items, hasMore, serverTime, effectiveSince,
                effectiveSinceId, last == null ? null : last.cursorMillis(), last == null ? null : last.id());
    }

    @Override
    public SyncChangesVO pullVaccineSchedule(Long sinceMillis, Long sinceId, Integer limit) {
        return pullBase(BaseDataTable.VACCINE_SCHEDULE, sinceMillis, sinceId, limit);
    }

    @Override
    public SyncChangesVO pullAlertThresholds(Long sinceMillis, Long sinceId, Integer limit) {
        return pullBase(BaseDataTable.ALERT_THRESHOLDS, sinceMillis, sinceId, limit);
    }

    @Override
    public SyncChangesVO pullPublishedVersions(Long sinceMillis, Long sinceId, Integer limit) {
        return pullBase(BaseDataTable.APP_VERSIONS, sinceMillis, sinceId, limit);
    }

    /**
     * 运营表通用增量拉取（M40）：与 {@link #pullBuiltinProducts} 同一条游标语义，
     * 只是数据来自 {@link BaseDataTable} 注册表（无 is_builtin 谓词、可有业务谓词）。
     */
    private SyncChangesVO pullBase(BaseDataTable table, Long sinceMillis, Long sinceId, Integer limit) {
        int pageLimit = SyncCursor.normalizeLimit(limit, DEFAULT_PULL_LIMIT, MAX_PULL_LIMIT);
        long serverTime = SyncCursor.now();
        long effectiveSince = SyncCursor.normalizeSince(sinceMillis, serverTime, CURSOR_FUTURE_TOLERANCE_MILLIS);
        long effectiveSinceId = sinceId == null ? 0L : Math.max(0L, sinceId);

        List<SyncRecordStore.BaseRow> rows = store.selectBaseChanges(
                table, SyncCursor.toLocalDateTime(effectiveSince), effectiveSinceId, pageLimit);
        boolean hasMore = rows.size() >= pageLimit;
        List<SyncRecordStore.BaseRow> page = hasMore ? rows.subList(0, pageLimit) : rows;

        List<Map<String, Object>> items = new ArrayList<>(page.size());
        for (SyncRecordStore.BaseRow row : page) {
            items.add(row.data());
        }
        SyncRecordStore.BaseRow last = page.isEmpty() ? null : page.get(page.size() - 1);
        return buildVo(table.apiName(), items, hasMore, serverTime, effectiveSince,
                effectiveSinceId, last == null ? null : last.cursorMillis(), last == null ? null : last.id());
    }

    /**
     * 组装 {@link SyncChangesVO}：行数不足以判 hasMore 的调用方已在外层算好；
     * 空页必须退回入参游标（客户端会把 nextSince 原样存下来，退 0 会导致下次全量重拉）。
     */
    private SyncChangesVO buildVo(String apiName, List<Map<String, Object>> items, boolean hasMore,
                                  long serverTime, long effectiveSince, long effectiveSinceId,
                                  Long lastCursorMillis, Long lastId) {
        Map<String, List<Map<String, Object>>> changes = new LinkedHashMap<>();
        changes.put(apiName, items);

        SyncChangesVO vo = new SyncChangesVO();
        vo.setChanges(changes);
        vo.setCount(items.size());
        vo.setHasMore(hasMore);
        vo.setServerTime(serverTime);
        if (lastCursorMillis == null) {
            // 空页退回入参游标：否则客户端会把游标清 0，下次变成全量重拉
            vo.setNextSince(effectiveSince);
            vo.setNextSinceId(effectiveSinceId);
        } else {
            vo.setNextSince(lastCursorMillis);
            vo.setNextSinceId(lastId);
        }
        return vo;
    }
}
