package com.babylog.biz.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 反馈行（管理端视图）：userId 透出（匿名提交为 null），不显示 create_by（那是审计口径） */
@Data
@Schema(description = "意见反馈行")
public class FeedbackVO {

    @Schema(description = "反馈 id")
    private Long id;

    @Schema(description = "提交用户 id（匿名提交为 null）")
    private Long userId;

    @Schema(description = "联系方式（可空）")
    private String contact;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "处理状态：PENDING/REPLIED/CLOSED")
    private String status;

    @Schema(description = "回复内容（可空）")
    private String reply;

    @Schema(description = "回复时刻（可空）")
    private LocalDateTime replyTime;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;
}
