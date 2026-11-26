package com.babylog.biz.kb.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.kb.dto.KbAskCmd;
import com.babylog.biz.kb.dto.KbFavoriteCmd;
import com.babylog.biz.kb.service.BabyLogKbQaService;
import com.babylog.biz.kb.service.KbFaqService;
import com.babylog.biz.kb.vo.KbAnswerVO;
import com.babylog.biz.kb.vo.KbCategoryVO;
import com.babylog.biz.kb.vo.KbFaqVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App 端育儿知识库端点（K6-S / M76）。
 *
 * <p>与既有 App 端点同口径：类级 {@code @SaCheckLogin(app-user)}、token 走
 * {@code Authorization: <裸值>}、userId 恒从 token 取，<b>客户端绝不发 userId</b>。
 * 类级鉴权同时满足 P0 ArchUnit A11 的鉴权信号。
 *
 * <p><b>路径不占 /sync/ 开头的同步端点</b>：FAQ / 收藏 / 问答全是服务端专用表或只读检索，
 * 不进 {@code SyncTable}、不参与 {@code /sync/changes}、不进 outbox。
 *
 * <p><b>免费 vs 按量</b>：浏览 / 分类 / 搜索 / 收藏全部免费；
 * 只有 {@code POST /ask} 计 {@code KB_QA} 额度（龙哥 2026-11-25 拍板 10 次/天 DAILY，
 * 门禁 C 授权后台参数中心可配）。V3 购买引导 / 虚拟支付 / 线下购买不在本节点范围。
 *
 * <p><b>免责</b>：每个问答响应（含降级）都带固定免责；客户端知识库页与回答区都必须展示
 * 「仅供参考，不构成医疗建议，异常请及时就医」（{@code 10} 号 §4.1 红线二）。
 */
@Tag(name = "App 育儿知识库（BabyLog）", description = "条目浏览 / 分类 / 搜索 / 收藏 / RAG 问答")
@RestController
@RequestMapping("/app/babylog/kb")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogKbController {

    private final KbFaqService faqService;
    private final BabyLogKbQaService qaService;

    public BabyLogKbController(KbFaqService faqService, BabyLogKbQaService qaService) {
        this.faqService = faqService;
        this.qaService = qaService;
    }

    @Operation(summary = "分类及该分类下已上架条目数（免费）")
    @GetMapping("/categories")
    public R<List<KbCategoryVO>> categories() {
        return R.ok(faqService.categories());
    }

    @Operation(summary = "条目浏览 / 分类 / 关键词搜索（免费）")
    @GetMapping("/faqs")
    public R<List<KbFaqVO>> faqs(@RequestParam(value = "category", required = false) String category,
                                 @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(faqService.list(currentUserId(), category, keyword));
    }

    @Operation(summary = "条目详情（免费；不存在或已下架返回 8464）")
    @GetMapping("/faqs/{faqId}")
    public R<KbFaqVO> faqDetail(@PathVariable("faqId") Long faqId) {
        if (faqId == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        return R.ok(faqService.detail(currentUserId(), faqId));
    }

    @Operation(summary = "我的收藏（免费；只返回仍上架的条目）")
    @GetMapping("/favorites")
    public R<List<KbFaqVO>> favorites() {
        return R.ok(faqService.favorites(currentUserId()));
    }

    @Operation(summary = "收藏条目（免费，幂等）")
    @PostMapping("/favorites")
    public R<Void> addFavorite(@RequestBody KbFavoriteCmd cmd) {
        if (cmd == null || cmd.getFaqId() == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        faqService.addFavorite(currentUserId(), cmd.getFaqId());
        return R.ok(null);
    }

    @Operation(summary = "取消收藏（免费，幂等）")
    @DeleteMapping("/favorites/{faqId}")
    public R<Void> removeFavorite(@PathVariable("faqId") Long faqId) {
        if (faqId == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        faqService.removeFavorite(currentUserId(), faqId);
        return R.ok(null);
    }

    @Operation(summary = "RAG 问答（按量 KB_QA；答案必带引用条目，无可引用资料不裸生成）")
    @PostMapping("/ask")
    public R<KbAnswerVO> ask(@RequestBody KbAskCmd cmd) {
        return R.ok(qaService.ask(currentUserId(), cmd));
    }

    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
