package com.babylog.biz.ai.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.ai.dto.AiParseRecordCmd;
import com.babylog.biz.ai.dto.AiWeeklyReportCmd;
import com.babylog.biz.ai.service.BabyLogAiService;
import com.babylog.biz.ai.vo.AiRecordDraftVO;
import com.babylog.biz.ai.vo.AiWeeklyReportVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V3.0 AI 端点（K5 / M73，契约 {@code 09} 会话 R + {@code 24}）。
 *
 * <p>与既有 App 端点同口径：类级 {@code @SaCheckLogin(app-user)}、token 走
 * {@code Authorization: <裸值>}、userId 恒从 token 取。<b>路径不占 {@code /sync/*}</b>：
 * AI 不是同步通道，{@code bl_ai_quota} 是服务端运营表、不进 {@code SyncTable}。
 *
 * <p><b>两个端点都只产草稿、不落库</b>：响应里没有记录 id、没有版本号，客户端必须走
 * 「展示 → 用户确认 → 既有 {@code /sync/push}」才可能落正式记录。
 *
 * <p>降级语义（{@code 09} R 红线）：AI 未配置返回 {@code 5020}、配置在但调用不通返回 {@code 5022}，
 * <b>不抛 HTTP 5xx、不返回空草稿、不输出伪造 AI 内容</b>。
 */
@Tag(name = "App AI 能力（BabyLog）", description = "自然语言补录草稿 / 育儿周报")
@RestController
@RequestMapping("/app/babylog/ai")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogAiController {

    private final BabyLogAiService aiService;

    public BabyLogAiController(BabyLogAiService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "自然语言补录 → 结构化草稿（不落库，需客户端确认后走 /sync/push）")
    @PostMapping("/parse-record")
    public R<AiRecordDraftVO> parseRecord(@RequestBody AiParseRecordCmd cmd) {
        return R.ok(aiService.parseRecord(currentUserId(), cmd));
    }

    @Operation(summary = "育儿周报（按需生成；数字服务端算，模型只写解读）")
    @PostMapping("/weekly-report")
    public R<AiWeeklyReportVO> weeklyReport(@RequestBody AiWeeklyReportCmd cmd) {
        return R.ok(aiService.weeklyReport(currentUserId(), cmd));
    }

    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
