package com.babylog.biz.sync.service;

import com.babylog.biz.sync.dto.SyncPushCmd;
import com.babylog.biz.sync.vo.SyncChangesVO;
import com.babylog.biz.sync.vo.SyncPushResultVO;

/**
 * 增量同步协议（App 端 ↔ 服务端）。
 *
 * <p>协议本体（02 号文档）：版本号裁决 + 软删除信号 + 服务端钟游标 + 批量推送。
 * 两条纪律贯穿全篇：
 * <ol>
 *   <li><b>拉取是幂等的</b>：同一段游标重复拉，客户端按 id upsert 即可，多收不脏；</li>
 *   <li><b>推送是幂等的</b>：同一记录推两次，第二次被 version 门禁挡掉，不会回退内容。</li>
 * </ol>
 * 这两条成立，客户端 outbox 的「失败退避重试直到成功」才有意义。
 */
public interface SyncService {

    /**
     * 按服务端游标增量拉取。
     *
     * @param userId      当前账号（拉取只返回 user_id = 自己的行）
     * @param sinceMillis 游标下界，null/0 = 从头全量
     * @param sinceId     同毫秒 tiebreak（上一页最后一条的 id），null = 0
     * @param categories  分类过滤（FEEDING/CARE/HEALTH/MEDICAL 逗号分隔），null/空 = 不限
     * @param limit       单页上限，null = 默认值
     */
    SyncChangesVO pull(long userId, Long sinceMillis, Long sinceId, String categories, Integer limit);

    /**
     * 批量推送（部分成功语义：逐条裁决，一条被拒不阻断同批其他记录）。
     */
    SyncPushResultVO push(long userId, SyncPushCmd cmd);
}
