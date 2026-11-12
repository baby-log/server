package com.babylog.biz.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * App 端反馈提交请求（匿名：不要求登录态；登录了会自动带上 userId）。
 */
@Data
public class FeedbackSubmitCmd {

    @Schema(description = "反馈内容")
    @NotBlank(message = "反馈内容不能为空")
    @Size(min = 5, max = 1000, message = "反馈内容须为 5-1000 字")
    private String content;

    @Schema(description = "联系方式（可空：愿意被回访才填）")
    @Size(max = 100, message = "联系方式最长 100 字")
    private String contact;
}
