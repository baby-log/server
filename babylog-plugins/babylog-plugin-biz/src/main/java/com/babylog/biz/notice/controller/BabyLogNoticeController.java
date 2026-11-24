package com.babylog.biz.notice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.notice.dto.NoticeConfirmCmd;
import com.babylog.biz.notice.dto.NoticeEventCmd;
import com.babylog.biz.notice.service.NoticeService;
import com.babylog.biz.notice.vo.NoticeChangesVO;
import com.babylog.biz.notice.vo.NoticeConfirmVO;
import com.babylog.biz.notice.vo.NoticeVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App / 小程序站内通知端点（K4 / M71，契约 {@code 24} ⑤ ⑥ ⑦）。
 *
 * <p>与既有 App 端点同口径：类级 {@code @SaCheckLogin(app-user)}、
 * token 走 {@code Authorization: <裸值>}、userId 恒从 token 取（请求传什么都不算数）。
 * <b>端点路径不占用 {@code /sync/*} 命名空间</b>，{@code bl_notice} 是服务端专用表、
 * 不进 {@code SyncTable}、不参与 {@code /sync/changes}。
 *
 * <p>V3 前零画面：{@code /events} 只接收结构化事件点（类型 + 时间点，零字节），
 * 是 K4 接收侧闭环的验证入口，K9 才接真实采集侧。
 */
@Tag(name = "App 站内通知（BabyLog）", description = "构造事件 / 增量拉 / 单人确认")
@RestController
@RequestMapping("/app/babylog/notice")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogNoticeController {

    private final NoticeService noticeService;

    public BabyLogNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "构造检测事件（K4 接收侧验证用；零画面，产待确认/无头异常通知）")
    @PostMapping("/events")
    public R<List<NoticeVO>> events(@RequestBody NoticeEventCmd cmd) {
        return R.ok(noticeService.construct(currentUserId(), cmd));
    }

    @Operation(summary = "增量拉通知（按 visibleUserIds 家庭读侧口径；双段游标 since+sinceId，nextSince 原样回传）")
    @GetMapping("/changes")
    public R<NoticeChangesVO> changes(@RequestParam(value = "since", required = false) Long since,
                                      @RequestParam(value = "sinceId", required = false) Long sinceId,
                                      @RequestParam(value = "limit", required = false) Integer limit) {
        return R.ok(noticeService.pull(currentUserId(), since, sinceId, limit));
    }

    @Operation(summary = "单人确认（version 乐观锁 CAS；先到者赢、后到者出 conflicts 拉取）")
    @PostMapping("/{id}/confirm")
    public R<NoticeConfirmVO> confirm(@PathVariable("id") Long id,
                                      @RequestBody NoticeConfirmCmd cmd) {
        return R.ok(noticeService.confirm(currentUserId(), id, cmd));
    }

    private static long currentUserId() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
