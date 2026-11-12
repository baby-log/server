package com.babylog.biz.feedback.dto;

import com.babylog.common.core.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/** 反馈分页查询（管理端） */
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackPageQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "处理状态：PENDING 待处理 / REPLIED 已回复 / CLOSED 已关闭")
    private String status;
}
