package com.babylog.biz.media.dto;

import lombok.Data;

/**
 * 确认 / 定稿帧转存命令（K2 / M69，契约 {@code 24} ④-4）。
 *
 * <p>待确认媒体被成员确认时调用：把该媒体行<b>关联到正式记录</b>
 * （{@code linkedRecordType} + {@code linkedRecordId} 同时非空），置
 * {@code confirmedAt} 与 {@code isFinalFrame=1}。
 *
 * <p><b>「转存」口径</b>：本批实现为<b>元数据关联</b>——不复制对象字节、不新建第二条媒体行。
 * 定稿帧因「已关联记录」被 {@code 24} ④-2 排除在淘汰之外 ⇒ 长期保留，原始帧流照常淘汰。
 * 这样零双份存储、零新通道，且用户回看正式记录附件时能取到同一对象。
 *
 * <p>入站 Long 按全局契约发字符串（雪花 19 位，JS 精度安全）。
 */
@Data
public class MediaConfirmCmd {

    /** 正式记录类型（如 VISIT / VACCINATION；与 linkedRecordId 同时必填） */
    private String linkedRecordType;

    /** 正式记录 id（雪花） */
    private Long linkedRecordId;
}
