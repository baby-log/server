package com.babylog.biz.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 自然语言补录草稿（K5 / M73，范围 B）。
 *
 * <p><b>这是草稿，不是记录</b>：{@code needConfirm} 恒为 {@code true}，服务端不落库、
 * 不写 19 张同步表、不进 outbox。客户端把它渲染成确认卡，用户点确认后走既有
 * {@code /sync/push} 落正式记录（与 K4「自动检测永不自动落库」同款纪律）。
 *
 * <p>全局契约：{@code startedAt} / {@code endedAt} 是 Long ⇒ JSON 里是字符串；
 * 缺省字段按全局 NON_NULL 直接不出现，客户端不得依赖 null 键存在。
 */
@Data
@Schema(description = "AI 补录草稿（不落库，需客户端确认后走 /sync/push）")
public class AiRecordDraftVO {

    @Schema(description = "记录类型（既有 RecordType 白名单内，如 BOTTLE / DIAPER / SLEEP）")
    private String recordType;

    @Schema(description = "业务发生时刻（epoch millis，字符串下发）")
    private Long startedAt;

    @Schema(description = "结束时刻（epoch millis；非计时类记录缺省）")
    private Long endedAt;

    @Schema(description = "毫升数（仅奶瓶/水等有毫升语义的类型）")
    private Integer amountMl;

    @Schema(description = "时长分钟（仅计时类记录）")
    private Integer durationMinutes;

    @Schema(description = "模型对原文的简短归纳（≤200 字，落到记录 note）")
    private String note;

    @Schema(description = "模型自评置信度 0~1；缺省表示模型没给")
    private Double confidence;

    @Schema(description = "恒为 true：草稿必须经用户确认后才落库")
    private Boolean needConfirm;

    @Schema(description = "本次调用后的剩余额度；-1 表示该功能不限次")
    private Integer quotaRemaining;
}
