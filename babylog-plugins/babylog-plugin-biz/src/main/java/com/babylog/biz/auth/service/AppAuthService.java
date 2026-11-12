package com.babylog.biz.auth.service;

import com.babylog.biz.auth.dto.EmailCodeCmd;
import com.babylog.biz.auth.dto.EmailLoginCmd;
import com.babylog.biz.auth.dto.PasswordLoginCmd;
import com.babylog.biz.auth.dto.RegisterCmd;
import com.babylog.biz.auth.dto.RefreshTokenCmd;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.biz.auth.vo.LoginTokenVO;

/**
 * App 端认证编排（BabyLog 补充端点，全部在 biz 插件内，不动 system 的 /app/auth/*）。
 *
 * <p>三件套：邮箱验证码登录（首发）、账密注册与账密登录（兜底）、refresh 换 access。
 * 三者共用同一套发 token / 落设备 / 建会话逻辑，保证「无论怎么进来，出门的凭证形状一样」。
 */
public interface AppAuthService {

    /** 发登录验证码 */
    CodeSendVO sendLoginCode(EmailCodeCmd cmd);

    /** 邮箱验证码登录（邮箱未注册则自动建档） */
    LoginTokenVO loginByEmailCode(EmailLoginCmd cmd);

    /** 账密注册：建 sys_user 账号并直接登录 */
    LoginTokenVO register(RegisterCmd cmd);

    /** 账密登录：account 可为用户名或邮箱 */
    LoginTokenVO loginByPassword(PasswordLoginCmd cmd);

    /** 用 refresh token 换新的 access token */
    LoginTokenVO refresh(RefreshTokenCmd cmd);

    /** 退出登录：注销当前 access，并（若带了）作废 refresh token */
    void logout(String refreshToken);
}
