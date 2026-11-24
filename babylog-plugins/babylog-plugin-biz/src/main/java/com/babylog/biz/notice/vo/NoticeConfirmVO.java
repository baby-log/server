package com.babylog.biz.notice.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单人确认结果（K4 / M71，契约 {@code 24} ⑥）。
 *
 * <p><b>时间口径</b>：{@code businessTime} 恒 = {@code detectedAt}（13:30 识别 / 13:50 确认
 * ⇒ 正式记录业务时间 = 13:30），{@code confirmedAt} 只作审计。客户端拿 {@code businessTime}
 * 走既有 {@code /sync/push} 落正式记录到 19 张同步表；服务端<b>不代写正式记录</b>
 * （自动检测永不自动落库保持，{@code 22} §7.2）。
 *
 * <p><b>先到者赢</b>：{@code confirmed=1} + {@code conflicts} 空；
 * <b>后到者出队拉取</b>：{@code confirmed=0} + {@code conflicts} 含一条（{@code 13} §1 #6），
 * 客户端据此把确认帧出队并触发增量拉，看到已确认态。
 *
 * <p>所有 Long → String；{@code confirmed} 用 Integer 0/1（绝不 true/false）。
 */
@Data
public class NoticeConfirmVO {

    /** 本次确认是否抢到：1 先到者赢 / 0 后到者出队拉取 */
    private Integer confirmed;

    /** 落正式记录的业务时间（= detectedAt，13:30；正式记录 startedAt 取此值） */
    private Long businessTime;

    /** 识别/检测时刻（epoch millis，13:30） */
    private Long detectedAt;

    /** 确认时刻（epoch millis，13:50；仅审计） */
    private Long confirmedAt;

    /** 确认人 user id（先到者；恒从 token 取） */
    private Long confirmedBy;

    /** 服务端当前 version（先到者推进后 / 后到者读到的现状） */
    private Long version;

    /** 冲突明细（后到者含一条，先到者为空）；结构对齐 M53 SyncConflictVO */
    private List<NoticeConflictVO> conflicts = new ArrayList<>();
}
