package com.babylog.biz.recordtype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 记录类型规则 VO（管理端分页/详情，M41） */
@Data
public class RecordTypeRuleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "记录类型标识（与客户端 RecordType 枚举 name 逐字一致）")
    private String recordType;

    @Schema(description = "分类：FEEDING/CARE/HEALTH/MEDICAL")
    private String category;

    @Schema(description = "默认开关：null=回落代码默认，0=默认关，1=默认开")
    private Integer enabled;

    @Schema(description = "起始月龄（天）：null=回落代码默认")
    private Integer startAgeDays;

    @Schema(description = "截止月龄（天）：null=回落代码默认")
    private Integer endAgeDays;

    @Schema(description = "记录页类内排序（小的在前）")
    private Integer sortOrder;

    @Schema(description = "类内分组键：null=默认不分组")
    private String groupKey;

    @Schema(description = "显示文案：null=回落代码默认文案")
    private String displayLabel;
}
