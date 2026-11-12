package com.babylog.biz.sync.service;

import com.babylog.biz.sync.vo.SyncChangesVO;

/**
 * 基础数据下发（M8）：内置产品等全局数据的增量通道。
 *
 * <p>为什么单独开通道而不并进 {@code /sync/changes}：同步通道的归属过滤是
 * {@code user_id = 自己}，而内置行是全局共享（user_id=0 占位）；把 products 表
 * 特殊化成「user_id=自己 OR is_builtin=1」会让 18 表统一的游标/归并逻辑开第一个
 * 例外口子。单独端点 + 单独游标，App 端只是多存一对游标。
 */
public interface BaseDataService {

    /**
     * 内置产品增量拉取。
     *
     * <p>行格式与 {@code /sync/changes} 完全一致（表名作键、行内 camelCase、
     * 含 deleted/version），App 端复用同一套解析与 upsert。
     *
     * @param sinceMillis 游标（{@code null} 视为 0，全量）
     * @param sinceId     同毫秒 tiebreak
     * @param limit       单页上限
     */
    SyncChangesVO pullBuiltinProducts(Long sinceMillis, Long sinceId, Integer limit);
}
