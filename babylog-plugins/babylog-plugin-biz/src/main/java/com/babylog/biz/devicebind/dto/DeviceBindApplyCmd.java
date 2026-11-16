package com.babylog.biz.devicebind.dto;

import com.babylog.biz.auth.dto.DeviceInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 电视端匿名申请设备码；本请求没有任何用户身份字段。 */
@Data
public class DeviceBindApplyCmd {

    @Schema(description = "待绑定电视的稳定设备信息")
    @NotNull(message = "设备信息不能为空")
    @Valid
    private DeviceInfo device;
}
