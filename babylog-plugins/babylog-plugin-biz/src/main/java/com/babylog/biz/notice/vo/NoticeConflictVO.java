package com.babylog.biz.notice.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单人确认冲突明细（K4 / M71，契约 {@code 24} ⑥-1 / {@code 13} §1 #6）。
 *
 * <p>结构与 {@code /sync/push} 的 {@code SyncConflictVO} 逐字对齐
 * （{@code table,id,serverVersion,clientVersion}，<b>不是 rejects</b>）：
 * 后到者收到 conflicts 后其确认帧<b>照常出队 + 触发拉取</b>，看到已确认态。
 *
 * <p>此处 {@code table} 恒为 {@code "bl_notice"}（服务端专用通知表，不是 19 张同步表之一），
 * 口径刻意沿用 M53 conflicts 结构以复用四端已有的冲突处置逻辑，不引入新范式。
 * 所有 Long → String（id / serverVersion / clientVersion 用字符串承载，防 JS 精度）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticeConflictVO {

    /** 冲突来源表标识（恒 "bl_notice"） */
    private String table;

    /** 通知 id（JSON 里按字符串承载） */
    private String id;

    /** 服务端当前版本号（先到者已推进） */
    private Long serverVersion;

    /** 客户端推送的期望版本号（后到者读到的是旧值） */
    private Long clientVersion;
}
