package com.babylog.message.facade;

import com.babylog.common.core.page.PageResult;
import com.babylog.message.api.dto.MessageDTO;
import com.babylog.message.api.dto.MessagePageQuery;
import com.babylog.message.api.dto.MessageSendCmd;
import com.babylog.message.api.facade.IMessageFacade;
import com.babylog.message.service.MessageService;
import com.babylog.starter.core.context.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IMessageFacade 本地实现（单体形态）
 *
 * <p>其他 Plugin 只注入 IMessageFacade 契约；微服务形态可替换为远程实现，消费方 0 改动。
 */
@Component
@RequiredArgsConstructor
public class MessageLocalFacade implements IMessageFacade {

    private final MessageService messageService;

    @Override
    public Long send(MessageSendCmd cmd) {
        return messageService.send(cmd);
    }

    @Override
    public PageResult<MessageDTO> pageByUser(Long userId, MessagePageQuery query) {
        return messageService.pageByUser(userId, query);
    }

    @Override
    public void markRead(Long userMessageId) {
        messageService.markRead(userMessageId, LoginContext.getUserId());
    }

    @Override
    public int markAllRead(Long userId) {
        return messageService.markAllRead(userId);
    }

    @Override
    public long countUnread(Long userId) {
        return messageService.countUnread(userId);
    }
}
