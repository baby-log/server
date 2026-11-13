package com.babylog.system.facade;

import com.babylog.system.api.dto.WechatSessionDTO;
import com.babylog.system.api.facade.IWechatMiniFacade;
import com.babylog.system.service.WechatMiniService;
import com.babylog.system.service.WechatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 微信小程序门面本地实现（单体形态；微服务形态可整体替换为远程实现） */
@Component
@RequiredArgsConstructor
public class WechatMiniLocalFacade implements IWechatMiniFacade {

    private final WechatMiniService wechatMiniService;

    @Override
    public WechatSessionDTO code2Session(String app, String code) {
        WechatSession session = wechatMiniService.code2Session(app, code);
        WechatSessionDTO dto = new WechatSessionDTO();
        dto.setOpenId(session.openId());
        dto.setUnionId(session.unionId());
        return dto;
    }
}
