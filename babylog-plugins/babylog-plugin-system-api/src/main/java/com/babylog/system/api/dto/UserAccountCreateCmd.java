package com.babylog.system.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 用户账号创建命令 */
@Data
public class UserAccountCreateCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名（必填） */
    private String username;

    /** 昵称（可空，空则取用户名） */
    private String nickname;

    /** 邮箱（可空） */
    private String email;

    /** 手机号（可空） */
    private String mobile;

    /** 原始密码（可空；为空表示无密码账号，由实现侧生成随机密码落库） */
    private String rawPassword;
}
