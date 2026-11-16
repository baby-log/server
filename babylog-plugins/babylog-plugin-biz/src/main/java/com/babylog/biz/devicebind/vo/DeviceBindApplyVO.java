package com.babylog.biz.devicebind.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DeviceBindApplyVO {

    @Schema(description = "电视展示、手机输入的 6 位数字码")
    private String userCode;

    @Schema(description = "电视私有轮询凭证，不在手机端展示")
    private String deviceCode;

    @Schema(description = "申请有效期（秒）")
    private int expiresIn;

    @Schema(description = "建议轮询间隔（秒）")
    private int pollIntervalSeconds;
}
