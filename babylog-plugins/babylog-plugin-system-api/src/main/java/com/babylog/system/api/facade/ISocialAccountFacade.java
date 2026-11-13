package com.babylog.system.api.facade;

import com.babylog.system.api.dto.SocialBindingDTO;

/**
 * 三方社交账号绑定契约。
 *
 * <p>绑定关系的唯一键是 {@code (channel, openId)}：一个微信身份只能绑一个系统账号。
 * 方法不依赖登录上下文。
 */
public interface ISocialAccountFacade {

    /**
     * 按渠道 + openId 查绑定。
     *
     * @param channel 渠道（如 wechat-mini）
     * @param openId  渠道内用户标识
     * @return 绑定记录；未绑定返回 null
     */
    SocialBindingDTO findByChannelOpenId(String channel, String openId);

    /**
     * 建立绑定。
     *
     * @param userId  系统用户 ID
     * @param channel 渠道
     * @param openId  渠道内用户标识
     * @param unionId 开放平台 unionId（可空）
     * @throws com.babylog.common.core.exception.ServiceException 该渠道身份已绑定
     *         其他账号时为 {@code SOCIAL_ALREADY_BOUND}
     */
    void bind(Long userId, String channel, String openId, String unionId);

    /**
     * 补写 unionId（微信有时首登不下发，后续登录遇到再补齐；仅在值变化时更新）。
     *
     * @param channel 渠道
     * @param openId  渠道内用户标识
     * @param unionId 新取得的 unionId
     */
    void updateUnionId(String channel, String openId, String unionId);
}
