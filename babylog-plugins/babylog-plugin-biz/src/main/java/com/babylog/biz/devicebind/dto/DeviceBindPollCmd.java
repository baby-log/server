package com.babylog.biz.devicebind.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 电视端以高熵 deviceCode 轮询；6 位 userCode 不能用于本端点。 */
@Data
public class DeviceBindPollCmd {

    @Schema(description = "申请时下发的高熵 deviceCode")
    @NotBlank(message = "deviceCode 不能为空")
    @Size(max = 128, message = "deviceCode 长度不能超过 128")
    private String deviceCode;
}
