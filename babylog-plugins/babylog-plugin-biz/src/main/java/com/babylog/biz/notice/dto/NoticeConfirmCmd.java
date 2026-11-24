package com.babylog.biz.notice.dto;

import lombok.Data;

/**
 * 单人确认命令（K4 / M71，契约 {@code 24} ⑥「复用 version 乐观锁 + conflicts」）。
 *
 * <p>两名成员同时确认同一待确认事件：各自带读到的 {@code version} 上行，服务端按
 * {@code version} 做原子 CAS（先到者赢）；后到者 version 落后 → 收 {@code conflicts}、
 * 其确认帧出队并触发拉取，看到已确认态（照 device-bind 领奖模式 / {@code 22} §二-8）。
 *
 * <p><b>不是 /sync/push</b>：这是通知端点上的原子确认，不往 19 张同步表塞行。
 * 正式记录由客户端拿 {@code businessTime=detectedAt} 走既有 {@code /sync/push} 落库。
 *
 * <p>入站 Long 按全局契约发字符串（雪花 19 位 + 时间戳，JS 精度安全），Spring 宽松转 Long。
 */
@Data
public class NoticeConfirmCmd {

    /** 客户端读到的通知 version（乐观锁期望值，必填 > 0） */
    private Long version;

    /** 确认时刻（epoch millis，13:50；可空则服务端用 now，仅作审计，正式记录业务时间不取此值） */
    private Long confirmedAt;

    /** 确认人显示名（可选，仅审计；身份恒从 token 取，不信客户端传值） */
    private String confirmedByLabel;
}
