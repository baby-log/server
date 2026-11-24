package com.babylog.biz.notice.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知增量拉取结果（K4 / M71，契约 {@code 24} ⑤-3 拉取式游标 / {@code 13} §1 #10）。
 *
 * <p><b>双段游标</b>：{@code nextSince}(server_updated_at millis) + {@code nextSinceId}(id)
 * 与 {@code /sync/changes} 同款，客户端把 {@code nextSince}/{@code nextSinceId} <b>原样回传</b>
 * （不透明游标，不做运算、不与手机时钟比较）。冒烟走「记游标 → 增量拉」，
 * {@code since=0} 全量看不见游标 bug（{@code 23} §2.2-13）。
 *
 * <p>所有 Long → String（全局 {@code WriteLongAsString}）。{@code hasMore} 用 Integer 0/1。
 */
@Data
public class NoticeChangesVO {

    /** 本次增量通知（按 visibleUserIds 下发，非家庭成员不可见） */
    private List<NoticeVO> notices = new ArrayList<>();

    /** 下一页游标：server_updated_at 的 epoch millis（原样回传） */
    private Long nextSince;

    /** 下一页游标：id（雪花；原样回传，双段去歧义） */
    private Long nextSinceId;

    /** 是否还有更多 0/1 */
    private Integer hasMore;

    /** 服务端当前时间（epoch millis，时钟漂移诊断参考） */
    private Long serverTime;
}
