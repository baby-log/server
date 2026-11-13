package com.babylog.system.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 三方社交账号绑定记录（渠道 + openId → 系统用户） */
@Data
public class SocialBindingDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 绑定的系统用户 ID（sys_user.id） */
    private Long userId;

    /** 渠道（wechat-mini / alipay-mini） */
    private String channel;

    /** 渠道内用户标识（openid） */
    private String openId;

    /** 开放平台 unionId（可空） */
    private String unionId;
}
