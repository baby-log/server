package com.babylog.biz.family.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 我的家庭信息。
 *
 * <p>{@code inviteCode} 只回给 OWNER（非创建者不需要也不该拿着邀请码拉人）；
 * 其他成员视角该字段为 null。
 */
@Data
@Schema(description = "当前账号所属的家庭")
public class FamilyVO {

    @Schema(description = "家庭 id")
    private Long id;

    @Schema(description = "家庭名称")
    private String name;

    @Schema(description = "邀请码（仅 OWNER 可见，用于邀人加入；可重置）")
    private String inviteCode;

    @Schema(description = "我的成员行 id（身份标识，App 端「谁记的」对齐用）")
    private Long memberId;

    @Schema(description = "我的角色：OWNER / CAREGIVER / VIEWER")
    private String role;

    @Schema(description = "家庭状态：ACTIVE")
    private String status;

    @Schema(description = "ACTIVE 成员数（含自己）")
    private Integer memberCount;
}
