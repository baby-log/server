package com.babylog.biz.appversion.dto;

import com.babylog.common.core.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/** 版本公告分页查询（管理端） */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppVersionPageQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "平台：ANDROID/IOS")
    private String platform;

    @Schema(description = "状态：DRAFT/PUBLISHED")
    private String status;
}
