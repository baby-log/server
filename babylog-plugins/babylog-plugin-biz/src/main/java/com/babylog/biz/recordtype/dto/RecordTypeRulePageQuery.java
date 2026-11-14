package com.babylog.biz.recordtype.dto;

import com.babylog.common.core.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/** 记录类型规则分页查询（管理端，M41） */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecordTypeRulePageQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "分类：FEEDING/CARE/HEALTH/MEDICAL")
    private String category;

    @Schema(description = "类型标识（模糊，如 BOTTLE）")
    private String recordType;
}
