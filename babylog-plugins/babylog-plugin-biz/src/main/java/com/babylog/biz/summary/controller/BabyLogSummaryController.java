package com.babylog.biz.summary.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.summary.service.TodaySummaryService;
import com.babylog.biz.summary.vo.TodaySummaryVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 今日汇总（V2.2 小程序第一批，只读聚合端点）。
 *
 * <p>为什么新增而不复用 /sync/changes：小程序轻入口每次打开只要「今天的几张数字」，
 * 拉全量增量流再客户端自算，首拉重一个量级还要在 JS 里复刻一份汇总逻辑。
 * 家庭/同步两端点本身零改动（M20 红线），本端点是 biz 内新增。
 */
@Tag(name = "今日汇总（BabyLog）", description = "小程序家庭轻入口 · 只读聚合")
@RestController
@RequestMapping("/app/babylog/summary")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogSummaryController {

    private final TodaySummaryService summaryService;

    public BabyLogSummaryController(TodaySummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Operation(summary = "今日汇总（按宝宝：喂养/尿布/睡眠聚合 + 最近动态）")
    @GetMapping("/today")
    public R<TodaySummaryVO> today() {
        return R.ok(summaryService.today(currentUserId()));
    }

    /** 与 family / sync / device 控制器同口径：会话被清时按 1002 回，让客户端走 refresh */
    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
