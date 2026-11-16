package com.babylog.biz.devicebind.vo;

import com.babylog.biz.auth.vo.LoginTokenVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DeviceBindPollVO {

    @Schema(description = "pending / approved / denied / expired")
    private String status;

    @Schema(description = "下次轮询等待秒数；终态为 0")
    private int retryAfterSeconds;

    @Schema(description = "仅一次性 approved 响应携带，与既有登录接口同形状")
    private LoginTokenVO login;
}
