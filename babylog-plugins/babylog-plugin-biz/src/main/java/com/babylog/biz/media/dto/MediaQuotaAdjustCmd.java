package com.babylog.biz.media.dto;

import lombok.Data;

/**
 * 后台动态调整宝宝媒体容量（K2 / M69，契约 {@code 24} ②-2 / ②-5）。
 *
 * <p>{@code initialBytes} 是初始容量、后台不改写；本命令只写 {@code adjustedBytes}。
 * 配额行不存在时按 {@code ownerUserId} 惰性建行（已存在行则保留原 {@code user_id}，不覆盖）——
 * 后台 ui 从宝宝列表能拿到归属用户 id（K3 接入侧消费），故此处必填。
 *
 * <p>入站 Long 按全局契约发字符串。
 */
@Data
public class MediaQuotaAdjustCmd {

    /** 宝宝 id（必填） */
    private Long childId;

    /** 宝宝归属用户 id（必填；仅「配额行尚不存在」时用于建行，读侧 visibleUserIds 口径） */
    private Long ownerUserId;

    /** 调整后容量（字节，必填且 >= 0；< 0 ⇒ 8425） */
    private Long adjustedBytes;
}
