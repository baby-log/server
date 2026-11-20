package com.babylog.biz.sync.store;

import com.babylog.biz.sync.support.BaseDataTable;
import com.babylog.biz.sync.support.SyncTable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 一张同步表的<b>类型化</b>读写适配（PivotOS 严格整改 S3）。
 *
 * <p>每张 {@link SyncTable} 注册一个实现，全部 SQL 由 MyBatis-Plus 从
 * {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper} 生成——本接口及其实现
 * 里不出现任何手写 SQL、字符串列名、{@code last()} 片段/{@code apply} 绕过。
 * 对外语义与旧 {@code JdbcSyncRecordStore} 逐条对齐（见各方法注释）。
 */
public interface SyncTableHandler {

    SyncTable table();

    /** 库内已存在的记录（含已软删除行——软删除本身也是要被覆盖的同步事实） */
    Map<Long, SyncRecordStore.Existing> selectExisting(Collection<Long> ids);

    /**
     * 游标增量拉取（含软删除行）：可见账号集合 + 双段游标
     * （serverUpdatedAt &gt; since，或 = since 且 id &gt; sinceId），
     * 按 (serverUpdatedAt, id) 稳定排序，至多 limit 行。
     */
    List<SyncRecordStore.Row> selectChanges(Collection<Long> userIds, LocalDateTime since,
                                            long sinceId, int limit);

    /**
     * 内置行增量拉取（M8 基础数据通道，不按 user_id 过滤）。
     *
     * @throws IllegalArgumentException 该表没有 is_builtin 列（当前只有 products 支持）
     */
    List<SyncRecordStore.Row> selectBuiltinChanges(LocalDateTime since, long sinceId, int limit);

    /**
     * 单条 version 乐观锁 upsert（类型化版，替代旧 INSERT … ON DUPLICATE KEY UPDATE）：
     * <ol>
     *   <li>版本门禁条件 UPDATE（{@code WHERE id = ? AND version < 推送版本}）——原子裁决，
     *       行已存在且库内更旧才整行覆盖（last-write-wins）；</li>
     *   <li>0 行 → 行不存在，INSERT（归属列 userId/ownerUserId 与审计列只在此时盖章，
     *       之后任何人再推同 id 都不会改写归属）；</li>
     *   <li>{@code DuplicateKeyException}（并发同 id 首插，一方已落库）→ 回退为
     *       版本门禁 UPDATE 裁决；仍 0 行 = 对方版本不旧于本方，幂等跳过。</li>
     * </ol>
     * 并发语义与旧 ODKU 等价：同一记录并发推送，高 version 覆盖低 version，
     * 归属与审计列不被覆盖方改写。
     */
    void upsert(Map<String, Object> payload, long userId);

    /**
     * M53：一条计时行结束后，把同一账号 / 宝宝 / 喂养类型下其它「进行中」行软删。
     *
     * <p>多设备各自点「开始喝奶」时，服务端会收到多条 in_progress=1 的行；
     * 若不做收敛，其它终端会一直显示多个「喝奶中」，且结束后残留成重复记录。
     * 软删走 deleted=1 的同步事实，各端下次增量拉取即把多余进行中卡移除。
     * 非 feedings 表默认不处理。
     *
     * @return 被软删的行数
     */
    default int closeOtherOngoingFeedings(long userId, long childId, String type, long keepId) {
        return 0;
    }
}
