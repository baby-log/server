package com.babylog.biz.feedback.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.babylog.biz.feedback.dto.FeedbackPageQuery;
import com.babylog.biz.feedback.dto.FeedbackReplyCmd;
import com.babylog.biz.feedback.service.FeedbackService;
import com.babylog.biz.feedback.vo.FeedbackVO;
import com.babylog.common.core.page.PageResult;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端反馈查看/回复（sys-admin 体系，M10）。权限字符对应菜单 SQL V2.2.9 的 5040 树。
 */
@Tag(name = "BabyLog 意见反馈（管理端）", description = "分页 / 回复")
@RestController
@RequestMapping("/babylog/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "反馈分页（状态过滤，提交时间倒序）")
    @GetMapping("/page")
    @SaCheckPermission(value = "babylog:feedback:list", type = StpSysUtil.TYPE)
    public R<PageResult<FeedbackVO>> page(FeedbackPageQuery query) {
        return R.ok(feedbackService.page(query));
    }

    @Operation(summary = "回复反馈（PENDING → REPLIED）")
    @PutMapping("/{id}/reply")
    @SaCheckPermission(value = "babylog:feedback:reply", type = StpSysUtil.TYPE)
    public R<Void> reply(@PathVariable("id") Long id, @Validated @RequestBody FeedbackReplyCmd cmd) {
        feedbackService.reply(id, cmd);
        return R.ok();
    }
}
