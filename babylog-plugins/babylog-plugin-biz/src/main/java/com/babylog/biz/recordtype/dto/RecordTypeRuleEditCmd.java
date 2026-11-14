package com.babylog.biz.recordtype.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 记录类型规则编辑请求（管理端，M41）。
 *
 * <p>recordType 走路径不可改（也不存在新增端点——边界：不能云端新增类型）；
 * 分类是只读展示字段，不在编辑范围内。enabled/startAgeDays/endAgeDays 允许为
 * null = 「该项不覆盖，回落客户端代码默认」，运营只想改文案时不用填齐。
 */
@Data
public class RecordTypeRuleEditCmd {

    @Schema(description = "默认开关：null=回落代码默认，0=默认关，1=默认开")
    @Min(value = 0, message = "开关只允许 0/1")
    @Max(value = 1, message = "开关只允许 0/1")
    private Integer enabled;

    @Schema(description = "起始月龄（天）：null=回落代码默认")
    @Min(value = 0, message = "起始月龄不能为负")
    @Max(value = 100000, message = "起始月龄超出合理范围")
    private Integer startAgeDays;

    @Schema(description = "截止月龄（天）：null=回落代码默认；与代码默认都 null=永不自动收起")
    @Min(value = 0, message = "截止月龄不能为负")
    @Max(value = 100000, message = "截止月龄超出合理范围")
    private Integer endAgeDays;

    @Schema(description = "记录页类内排序（小的在前）")
    @Min(value = 0, message = "排序不能为负")
    @Max(value = 9999, message = "排序超出合理范围")
    private Integer sortOrder;

    @Schema(description = "类内分组键：null/空=默认不分组")
    @Size(max = 32, message = "分组键最长 32 字")
    private String groupKey;

    @Schema(description = "显示文案：null/空=回落代码默认文案")
    @Size(max = 32, message = "显示文案最长 32 字")
    private String displayLabel;
}
