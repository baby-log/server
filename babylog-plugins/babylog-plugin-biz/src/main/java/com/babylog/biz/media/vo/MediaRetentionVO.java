package com.babylog.biz.media.vo;

import lombok.Data;

/**
 * 淘汰任务执行结果（诊断端点 / 单测 / xxl-job 日志共用）。
 */
@Data
public class MediaRetentionVO {

    /** 已确认超 2h 被淘汰的行数 */
    private int confirmedExpired;
    /** 未确认超 3 天被淘汰的行数 */
    private int unconfirmedExpired;
    /** 容量满「删最早」被淘汰的行数 */
    private int capacityEvicted;
    /** 因已关联正式记录 / 定稿帧而跳过保护的行数（本批次统计） */
    private int skippedLinked;
    /** 物理删失败而保留 DB 行、留待下次重试的行数 */
    private int physicalDeleteFailed;
    /** 合计淘汰行数（DB 逻辑删成功） */
    private int totalDeleted;
}
