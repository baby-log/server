package com.babylog.biz.sync.service.impl;

import com.babylog.biz.sync.service.BaseDataService;
import com.babylog.biz.sync.store.SyncRecordStore;
import com.babylog.biz.sync.support.SyncCursor;
import com.babylog.biz.sync.support.SyncTable;
import com.babylog.biz.sync.vo.SyncChangesVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据下发实现（内置产品）。
 *
 * <p>游标逻辑与 {@code SyncServiceImpl#pull} 同构（双段、normalize、空页退回原游标），
 * 只是单表、无分类过滤、不过滤归属——归一化函数已下沉 {@link SyncCursor} 保证两通道一套口径。
 * App 端解析完全复用 {@code /sync/changes} 的代码路径（changes 的键仍是表名 products）。
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

        Map<String, List<Map<String, Object>>> changes = new LinkedHashMap<>();
        List<Map<String, Object>> products = new ArrayList<>(page.size());
        for (SyncRecordStore.Row row : page) {
            products.add(row.data());
        }
        changes.put(SyncTable.PRODUCTS.apiName(), products);

        SyncChangesVO vo = new SyncChangesVO();
        vo.setChanges(changes);
        vo.setCount(page.size());
        vo.setHasMore(hasMore);
        vo.setServerTime(serverTime);
        if (page.isEmpty()) {
            // 空页退回入参游标：否则客户端会把游标清 0，下次变成全量重拉
            vo.setNextSince(effectiveSince);
            vo.setNextSinceId(effectiveSinceId);
        } else {
            SyncRecordStore.Row last = page.get(page.size() - 1);
            vo.setNextSince(last.cursorMillis());
            vo.setNextSinceId(last.id());
        }
        return vo;
    }
}
