package com.babylog.biz.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 内置产品行（管理端视图）。version 一并透出，便于排查 base 通道增量下发 */
@Data
@Schema(description = "内置产品行（管理端）")
public class ProductVO {

    @Schema(description = "产品 id（内置行固定 1~99 段）")
    private Long id;

    @Schema(description = "分类：FORMULA/SUPPLEMENT/MEDICINE/DIAPER")
    private String category;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "品名")
    private String name;

    @Schema(description = "规格")
    private String spec;

    @Schema(description = "默认剂量")
    private String defaultDose;

    @Schema(description = "展示排序：数字越大越靠前（M67，后台可维护）")
    private Integer sortOrder;

    @Schema(description = "被设为默认产品的偏好行数（M67，跨账号聚合，只读；后台仅查看默认值、不提供设置/取消）")
    private Long defaultCount;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "图标（plugin-file 的 file_id）")
    private String iconPath;

    @Schema(description = "上架开关：1 在售 0 下架")
    private Integer enabled;

    @Schema(description = "内置标记（本页恒为 1）")
    private Integer isBuiltin;

    @Schema(description = "App 端最后使用时刻（epoch millis，仅展示）")
    private Long lastUsedAt;

    @Schema(description = "同步版本号（每次维护 +1，App 端 base 通道据此增量下发）")
    private Long version;

    @Schema(description = "服务端最后维护时刻（epoch millis）")
    private Long serverUpdatedAt;
}
