package com.babylog.biz.product.dto;

import com.babylog.common.core.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/** 内置产品分页查询（管理端）。固定只查 is_builtin=1：用户自建产品不在此页露出 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductPageQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "分类：FORMULA/SUPPLEMENT/MEDICINE/DIAPER")
    private String category;

    @Schema(description = "品牌（模糊）")
    private String brand;

    @Schema(description = "品名（模糊）")
    private String name;

    @Schema(description = "上架状态：1 在售 0 下架")
    private Integer enabled;
}
