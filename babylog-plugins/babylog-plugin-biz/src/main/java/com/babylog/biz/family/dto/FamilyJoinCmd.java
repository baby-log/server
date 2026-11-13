package com.babylog.biz.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭邀请码加入家庭（新成员默认 VIEWER）。
 */
@Data
public class FamilyJoinCmd {

    @Schema(description = "邀请码（大小写不敏感）", example = "ABCD2345")
    @NotBlank(message = "邀请码不能为空")
    @Size(max = 16, message = "邀请码最长 16 位")
    private String inviteCode;
}
