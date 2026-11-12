package com.babylog.biz.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 验证码发送结果：只回「多久有效 / 多久后可重发」，绝不回验证码本身 */
@Data
public class CodeSendVO {

    @Schema(description = "验证码有效期（秒）")
    private long expireSeconds;

    @Schema(description = "多久后可再次发送（秒）")
    private long resendAfterSeconds;
}
