package com.babylog.biz.media.vo;

import lombok.Data;

/**
 * 宝宝容量配额 VO（App 读侧 / 后台调整后回显）。
 *
 * <p>契约 {@code 24} ②-5：读侧必须能展示「初始容量」，后台可动态调整。
 * 全部容量字段都是 Long ⇒ 全局 Long→String（前端精度安全）。
 * {@code usedBytes} 由 {@code bl_media_file} 实时聚合，不落冗余列。
 */
@Data
public class MediaQuotaVO {

    private Long id;
    private Long childId;
    /** 初始容量（字节） */
    private Long initialBytes;
    /** 后台调整后容量（字节）；null = 未调整，JSON 中缺省该键 */
    private Long adjustedBytes;
    /** 生效容量 = adjustedBytes != null ? adjustedBytes : initialBytes */
    private Long effectiveBytes;
    /** 已用容量（字节，由文件行聚合） */
    private Long usedBytes;
    /** 剩余容量（字节，可为负=已超配额，由淘汰任务收敛） */
    private Long remainingBytes;
}
