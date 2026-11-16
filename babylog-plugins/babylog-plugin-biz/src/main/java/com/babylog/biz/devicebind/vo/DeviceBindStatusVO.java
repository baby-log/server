package com.babylog.biz.devicebind.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeviceBindStatusVO {

    @Schema(description = "approved 或 denied")
    private String status;
}
