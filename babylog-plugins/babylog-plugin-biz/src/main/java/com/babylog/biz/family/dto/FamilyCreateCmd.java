package com.babylog.biz.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建家庭（默认 owner）。
 */
@Data
public class FamilyCreateCmd {

    @Schema(description = "家庭名称", example = "我们家")
    @NotBlank(message = "家庭名称不能为空")
    @Size(max = 50, message = "家庭名称最长 50 字")
    private String name;
}
