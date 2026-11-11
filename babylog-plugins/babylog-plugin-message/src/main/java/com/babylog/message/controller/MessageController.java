package com.babylog.message.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.babylog.common.core.page.PageResult;
import com.babylog.common.core.result.R;
import com.babylog.message.api.dto.MessageSendCmd;
import com.babylog.message.domain.dto.MessageManageQuery;
import com.babylog.message.domain.dto.MessageSendRequest;
import com.babylog.message.domain.vo.MessageManageVO;
import com.babylog.message.service.MessageService;
import com.babylog.starter.auth.account.StpSysUtil;
import com.babylog.starter.web.annotation.RepeatSubmit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 消息管理（后台发送 + 全量分页） */
@Tag(name = "消息管理", description = "后台消息发送 + 全量分页")
@RestController
@RequestMapping("/message/manage")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "发送日志分页")
    @GetMapping("/page")
    @SaCheckPermission(value = "message:message:list", type = StpSysUtil.TYPE)
    public R<PageResult<MessageManageVO>> page(MessageManageQuery query) {
        return R.ok(messageService.pageAll(query));
    }

    @Operation(summary = "发送日志详情")
    @GetMapping("/{id}")
    @SaCheckPermission(value = "message:message:query", type = StpSysUtil.TYPE)
    public R<MessageManageVO> get(@PathVariable Long id) {
        return R.ok(messageService.getManage(id));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    @RepeatSubmit
    @SaCheckPermission(value = "message:message:send", type = StpSysUtil.TYPE)
    public R<Long> send(@Validated @RequestBody MessageSendRequest request) {
        MessageSendCmd cmd = new MessageSendCmd();
        BeanUtils.copyProperties(request, cmd);
        return R.ok(messageService.send(cmd));
    }
}
