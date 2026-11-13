package com.babylog.biz.family.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 家庭成员列表行。
 */
@Data
@Schema(description = "家庭的一个成员")
public class FamilyMemberVO {

    @Schema(description = "成员行 id（改角色/踢出端点用）")
    private Long id;

    @Schema(description = "成员用户 id（sys_user.id）")
    private Long userId;

    @Schema(description = "成员显示名（App 端展示「谁记的」）")
    private String displayName;

    @Schema(description = "角色：OWNER / CAREGIVER / VIEWER")
    private String role;

    @Schema(description = "状态：ACTIVE 在家 / REMOVED 已被移除")
    private String status;

    @Schema(description = "用户来源：APP / WX_MINI")
    private String userType;

    @Schema(description = "加入时刻（epoch millis）")
    private Long joinedAt;
}
