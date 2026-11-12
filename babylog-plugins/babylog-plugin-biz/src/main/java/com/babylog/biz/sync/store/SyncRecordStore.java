package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.SyncTable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 同步记录存储（18 张 bl_ 表的通用读写）。
 *
 * <p>抽成接口是为了让同步编排可单测：{@link SyncServiceImpl} 的契约、
 * 版本门禁、归属校验、批量上限全部用 Mock 覆盖，不连库。
 * 真实 SQL 的正确性由 dev 环境 curl 联调兜底（造数据 → push → changes 取回）。
 */
public interface SyncRecordStore {

    /** 库内已存在的记录（含已软删除行——软删除本身也是要被覆盖的同步事实） */
    record Existing(long userId, long version) {
    }

    /** 拉取出来的一行：{@code cursorMillis} 用于跨表归并排序与 nextSince 计算 */
    record Row(SyncTable table, long id, long cursorMillis, Map<String, Object> data) {
    }

    /**
     * 批量取库内已存在的行（含软删除）。
     *
     * @return id → 归属与版本；不存在的 id 不在 map 里
     */
    Map<Long, Existing> selectExisting(SyncTable table, Collection<Long> ids);

    /**
     * 游标增量拉取（<b>含软删除行</b>：deleted=1 也是客户端要应用的变更）。
     *
     * @param since   服务端游标（{@code server_updated_at} 下界，含同值）
     * @param sinceId 同毫秒 tiebreak（上一页最后一条的 id）
     * @param limit   单表上限（全局上限由服务层再裁一次）
     */
    List<Row> selectChanges(SyncTable table, long userId, LocalDateTime since, long sinceId, int limit);

    /**
     * 单条 version 乐观锁 upsert。
     *
     * <p>语义：主键不存在 → 插入；主键已存在且推送 version &gt; 库内 version → <b>整行</b>覆盖
     * （last-write-wins，不做字段级合并）；否则什么都不改（旧版本或同版本一律库内优先）。
     *
     * @param payload 客户端载荷（camelCase 属性名，只取该表登记过的载荷列）
     * @param userId  当前账号：既是归属列取值，也是审计列的 actor
     */
    void upsert(SyncTable table, Map<String, Object> payload, long userId);
}
