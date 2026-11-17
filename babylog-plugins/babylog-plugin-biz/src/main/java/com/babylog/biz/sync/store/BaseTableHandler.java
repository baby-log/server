package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.BaseDataTable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一张基础数据运营表的<b>类型化</b>增量下发适配（PivotOS 严格整改 S3，M40）。
 *
 * <p>base 通道是单向下发：只有游标拉取，没有 push 载荷/归属盖章/版本门禁。
 * 每个 {@link BaseDataTable} 注册一个实现，SQL 全部由 MyBatis-Plus 类型化生成。
 */
public interface BaseTableHandler {

    BaseDataTable table();

    /**
     * 增量拉取（含软删除行：运营表的软删除同样是客户端要应用的事实）：
     * 双段游标 + (serverUpdatedAt, id) 稳定排序 + 至多 limit 行；
     * 业务谓词（如版本表仅 PUBLISHED）由注册时登记的类型化条件承担。
     */
    List<SyncRecordStore.BaseRow> selectChanges(LocalDateTime since, long sinceId, int limit);
}
