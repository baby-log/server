package com.babylog.biz.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改成员角色（仅 OWNER 可操作；OWNER 本身不可指定也不可转让——01B 第六节兜底语义）。
 */
@Data
public class MemberRoleCmd {

    @Schema(description = "新角色：CAREGIVER / VIEWER", example = "CAREGIVER")
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "CAREGIVER|VIEWER", message = "角色只能是 CAREGIVER 或 VIEWER（OWNER 不可指定）")
    private String role;
}
