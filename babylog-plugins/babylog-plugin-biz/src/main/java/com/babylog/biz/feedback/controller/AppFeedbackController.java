package com.babylog.biz.feedback.controller;

import com.babylog.biz.feedback.dto.FeedbackSubmitCmd;
import com.babylog.biz.feedback.service.FeedbackService;
import com.babylog.common.core.result.R;
import com.babylog.starter.core.context.LoginContext;
import com.babylog.starter.web.annotation.RepeatSubmit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 端反馈提交（M10）。
 *
 * <p><b>匿名端点</b>：不要求登录——未开云同步的用户也应能反馈。登录了会自动带上
 * userId（LoginContext 未绑定返回 null，匿名落库），联系方式可空。
 *
 * <p>A11 口径：方法体读了 LoginContext（③ 业务代码兜底轨），不登记 ANONYMOUS_ENDPOINTS。
 * 防刷走 {@code @RepeatSubmit}（拦截器指纹：登录人，未登录用 IP；窗口默认 5s）。
 */
@Tag(name = "App 意见反馈（BabyLog）", description = "匿名提交反馈")
@RestController
@RequestMapping("/app/babylog/feedback")
public class AppFeedbackController {

    private final FeedbackService feedbackService;

    public AppFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "提交反馈（匿名可用；登录后自动关联账号，联系方式可空）")
    @PostMapping
    @RepeatSubmit(interval = 10_000, message = "反馈提交过于频繁，请稍后再试")
    public R<Void> submit(@Validated @RequestBody FeedbackSubmitCmd cmd) {
        // 未登录返回 null → 匿名反馈；这里不抛 401（与 M5/M6 端点的 currentUserId 不同）
        feedbackService.submit(cmd, LoginContext.getUserId());
        return R.ok();
    }
}
