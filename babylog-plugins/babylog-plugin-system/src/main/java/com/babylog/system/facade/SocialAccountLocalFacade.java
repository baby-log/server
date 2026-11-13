package com.babylog.system.facade;

import com.babylog.system.api.dto.SocialBindingDTO;
import com.babylog.system.api.facade.ISocialAccountFacade;
import com.babylog.system.domain.entity.SysSocialUser;
import com.babylog.system.service.SocialUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 三方社交绑定门面本地实现（单体形态；微服务形态可整体替换为远程实现） */
@Component
@RequiredArgsConstructor
public class SocialAccountLocalFacade implements ISocialAccountFacade {

    private final SocialUserService socialUserService;

    @Override
    public SocialBindingDTO findByChannelOpenId(String channel, String openId) {
        SysSocialUser entity = socialUserService.findByChannelOpenId(channel, openId);
        return entity == null ? null : toDto(entity);
    }

    @Override
    public void bind(Long userId, String channel, String openId, String unionId) {
        socialUserService.bind(userId, channel, openId, unionId);
    }

    @Override
    public void updateUnionId(String channel, String openId, String unionId) {
        if (!StringUtils.hasText(unionId)) {
            return;
        }
        SysSocialUser entity = socialUserService.findByChannelOpenId(channel, openId);
        if (entity == null || unionId.equals(entity.getUnionId())) {
            return;
        }
        entity.setUnionId(unionId);
        socialUserService.updateById(entity);
    }

    private static SocialBindingDTO toDto(SysSocialUser entity) {
        SocialBindingDTO dto = new SocialBindingDTO();
        dto.setUserId(entity.getUserId());
        dto.setChannel(entity.getChannel());
        dto.setOpenId(entity.getOpenId());
        dto.setUnionId(entity.getUnionId());
        return dto;
    }
}
