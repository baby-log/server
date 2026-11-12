package com.babylog.biz.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量推送结果（部分成功语义）。
 */
@Data
public class SyncPushResultVO {

    @Schema(description = "本次提交的记录总数")
    private int total;

    @Schema(description = "实际落库条数（新增 + 版本更新）")
    private int upserted;

    @Schema(description = "跳过条数（库内 version 不低于推送 version，服务端优先；客户端随后拉取即对齐）")
    private int skipped;

    @Schema(description = "被拒明细（不影响其他记录落库）")
    private List<SyncPushRejectVO> rejected = new ArrayList<>();

    @Schema(description = "服务端当前时间（同一套游标编码）")
    private long serverTime;
}
