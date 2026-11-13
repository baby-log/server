package com.babylog.system.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信 code2session 结果。
 *
 * <p>不含 session_key（对称解密密钥不出 system 插件边界，调用方用不到）。
 */
@Data
public class WechatSessionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 渠道内用户标识（openid，必非空） */
    private String openId;

    /** 开放平台 unionId（可空：未绑定开放平台或首登未下发） */
    private String unionId;
}
