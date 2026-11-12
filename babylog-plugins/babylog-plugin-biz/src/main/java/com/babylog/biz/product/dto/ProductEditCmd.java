package com.babylog.biz.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 内置产品编辑请求（管理端）。id 不可改、is_builtin 不可改、lastUsedAt 不可改 */
@Data
public class ProductEditCmd {

    @Schema(description = "分类：FORMULA/SUPPLEMENT/MEDICINE/DIAPER")
    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "FORMULA|SUPPLEMENT|MEDICINE|DIAPER", message = "分类须为 FORMULA/SUPPLEMENT/MEDICINE/DIAPER")
    private String category;

    @Schema(description = "品牌")
    @NotBlank(message = "品牌不能为空")
    @Size(max = 100, message = "品牌最长 100 字")
    private String brand;

    @Schema(description = "品名")
    @NotBlank(message = "品名不能为空")
    @Size(max = 100, message = "品名最长 100 字")
    private String name;

    @Schema(description = "规格")
    @Size(max = 100, message = "规格最长 100 字")
    private String spec;

    @Schema(description = "默认剂量")
    @Size(max = 100, message = "默认剂量最长 100 字")
    private String defaultDose;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注最长 500 字")
    private String note;

    @Schema(description = "图标（plugin-file 的 file_id）")
    @Size(max = 255, message = "图标路径最长 255 字")
    private String iconPath;

    @Schema(description = "上架开关：1 在售 0 下架")
    @NotNull(message = "上架状态不能为空")
    @Min(value = 0, message = "上架状态只允许 0/1")
    @Max(value = 1, message = "上架状态只允许 0/1")
    private Integer enabled;
}
