package com.babylog.biz.auth.service;

import com.babylog.biz.auth.vo.CodeSendVO;

/**
 * 邮箱验证码：发送（含限流）与校验（含错误次数作废）。
 *
 * <p>安全口径：验证码只落 Redis、不回显、不写日志；发送成功的日志只打脱敏邮箱。
 */
public interface EmailCodeService {

    /**
     * 发送登录验证码。
     *
     * @param rawEmail 客户端原始输入（内部做 trim + 小写归一化 + 格式校验）
     * @return 有效期与可重发间隔，便于客户端倒计时
     */
    CodeSendVO sendLoginCode(String rawEmail);

    /**
     * 校验验证码，校验通过即消费（单次有效）。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8370 错误/过期/超次作废
     */
    void verify(String rawEmail, String code);

    /** 邮箱归一化（trim + 小写），供调用方在其他环节复用同一口径 */
    String normalize(String rawEmail);
}
