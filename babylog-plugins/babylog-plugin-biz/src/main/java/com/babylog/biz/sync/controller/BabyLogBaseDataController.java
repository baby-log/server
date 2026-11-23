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
 * <p>与 19 表同步通道并列的<b>全局数据通道</b>：内置产品（is_builtin=1）不属于任何账号，
 * 归属过滤会把它们滤掉，所以单独开端点。返回结构与 {@code /sync/changes} 完全一致
 * （SyncChangesVO），App 端复用同一套解析与 upsert，只是游标独立存一对。
 *
 * <p>鉴权与同步端点同口径：类上 {@code @SaCheckLogin(app-user)}——登录是前提
 * （防匿名批量拉基础数据），但内容对全部登录用户相同。
 */
@Tag(name = "App 基础数据（BabyLog）", description = "内置产品/疫苗时间表/异常提醒阈值/版本公告增量下发（M40 扩范围）")
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

    @Operation(summary = "疫苗时间表增量下发（M40 基础数据通道，changes 键 vaccine_schedule）")
    @GetMapping("/vaccine-schedule")
    public R<SyncChangesVO> vaccineSchedule(@RequestParam(required = false) Long since,
                                            @RequestParam(required = false) Long sinceId,
                                            @RequestParam(required = false) Integer limit) {
        return R.ok(baseDataService.pullVaccineSchedule(since, sinceId, limit));
    }

    @Operation(summary = "异常提醒阈值增量下发（M40，changes 键 alert_thresholds，客户端替换本地默认值）")
    @GetMapping("/alert-thresholds")
    public R<SyncChangesVO> alertThresholds(@RequestParam(required = false) Long since,
                                            @RequestParam(required = false) Long sinceId,
                                            @RequestParam(required = false) Integer limit) {
        return R.ok(baseDataService.pullAlertThresholds(since, sinceId, limit));
    }

    @Operation(summary = "版本与公告增量下发（M40，只下发 PUBLISHED 行，changes 键 app_versions）")
    @GetMapping("/versions")
    public R<SyncChangesVO> versions(@RequestParam(required = false) Long since,
                                     @RequestParam(required = false) Long sinceId,
                                     @RequestParam(required = false) Integer limit) {
        return R.ok(baseDataService.pullPublishedVersions(since, sinceId, limit));
    }

    @Operation(summary = "记录类型云端规则增量下发（M41，changes 键 record_type_rules，客户端规则缓存表消费）")
    @GetMapping("/record-type-rules")
    public R<SyncChangesVO> recordTypeRules(@RequestParam(required = false) Long since,
                                            @RequestParam(required = false) Long sinceId,
                                            @RequestParam(required = false) Integer limit) {
        return R.ok(baseDataService.pullRecordTypeRules(since, sinceId, limit));
    }
}
