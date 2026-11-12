package com.babylog.biz.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理端反馈回复请求（PENDING → REPLIED） */
@Data
public class FeedbackReplyCmd {

    @Schema(description = "回复内容（App 端暂无回显入口，先落库存档）")
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容最长 1000 字")
    private String reply;
}
