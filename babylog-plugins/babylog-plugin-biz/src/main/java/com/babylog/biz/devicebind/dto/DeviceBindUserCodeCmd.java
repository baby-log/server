package com.babylog.biz.devicebind.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 手机确认或拒绝；授权账号只从登录上下文取，刻意不提供 userId 字段。 */
@Data
public class DeviceBindUserCodeCmd {

    @Schema(description = "电视屏幕展示的 6 位数字码", example = "012345")
    @NotBlank(message = "userCode 不能为空")
    @Pattern(regexp = "\\d{6}", message = "userCode 必须为 6 位数字")
    private String userCode;
}
