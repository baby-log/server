package com.babylog.biz.sync.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.sync.service.BaseDataService;
import com.babylog.biz.sync.vo.SyncChangesVO;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 端基础数据下发端点（M8）。
 *
 * <p>与 18 表同步通道并列的<b>全局数据通道</b>：内置产品（is_builtin=1）不属于任何账号，
 * 归属过滤会把它们滤掉，所以单独开端点。返回结构与 {@code /sync/changes} 完全一致
 * （SyncChangesVO），App 端复用同一套解析与 upsert，只是游标独立存一对。
 *
 * <p>鉴权与同步端点同口径：类上 {@code @SaCheckLogin(app-user)}——登录是前提
 * （防匿名批量拉基础数据），但内容对全部登录用户相同。
 */
@Tag(name = "App 基础数据（BabyLog）", description = "内置产品增量下发")
@RestController
@RequestMapping("/app/babylog/base")
@SaCheckLogin(type = StpAppUtil.TYPE)
public class BabyLogBaseDataController {

    private final BaseDataService baseDataService;

    public BabyLogBaseDataController(BaseDataService baseDataService) {
        this.baseDataService = baseDataService;
    }

    @Operation(summary = "内置产品增量拉取（游标口径与 /sync/changes 相同，独立存游标）")
    @GetMapping("/products")
    public R<SyncChangesVO> products(@RequestParam(required = false) Long since,
                                     @RequestParam(required = false) Long sinceId,
                                     @RequestParam(required = false) Integer limit) {
        return R.ok(baseDataService.pullBuiltinProducts(since, sinceId, limit));
    }
}
