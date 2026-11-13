package com.babylog.system.api.facade;

import com.babylog.system.api.dto.WechatSessionDTO;

/**
 * 微信小程序 API 契约（跨 Plugin 场景的 code2session 通道）。
 *
 * <p>方法不依赖登录上下文；access_token 的获取与缓存不出 system 插件边界。
 */
public interface IWechatMiniFacade {

    /**
     * js_code 换 openid / unionId。
     *
     * @param app  小程序应用标识（对应 {@code babylog.wechat.miniapp.configs.<app>} 配置位）
     * @param code wx.login() 下发的 js_code（一次性，5 分钟有效）
     * @return 会话标识（openId 必非空）
     * @throws com.babylog.common.core.exception.ServiceException 应用未配置时为
     *         {@code SOCIAL_NOT_CONFIGURED}，code 无效/已使用或微信接口失败时为
     *         {@code SOCIAL_CODE_INVALID} / {@code SOCIAL_API_FAILED}
     */
    WechatSessionDTO code2Session(String app, String code);
}
