package com.babylog.biz.adminconsole.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.adminconsole.service.AdminConsoleService;
import com.babylog.biz.adminconsole.vo.AdminPageResult;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M53：BabyLog 运营数据只读查询（sys-admin 体系）。
 *
 * <p>用户验收问题 4：后台原只有内置产品 / 设备 / 版本 / 反馈 / 记录类型规则五页，
 * 看不到「所有人、家庭、宝宝、喂养记录、疫苗、提醒」。本类补齐跨用户只读查询。
 *
 * <p><b>边界</b>：全部为 GET、全部只读、全部要求 sys-admin 登录态（{@code StpSysUtil.TYPE}），
 * 与 App 端 {@code /app/babylog/**} 的 app-user 体系完全隔离。婴儿健康数据属敏感个人信息，
 * 本批不提供编辑、删除、导出端点；页面文案需提示「只读、操作留痕」。
 */
@Tag(name = "BabyLog 运营数据（管理端只读）", description = "用户 / 家庭 / 宝宝 / 记录明细 / 疫苗 / 提醒 / 概览")
@RestController
@RequestMapping("/babylog/admin")
@SaCheckLogin(type = StpSysUtil.TYPE)
public class AdminConsoleController {

    private final AdminConsoleService service;

    public AdminConsoleController(AdminConsoleService service) {
        this.service = service;
    }

    @Operation(summary = "运营概览（七项数量指标）")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(service.overview());
    }

    @Operation(summary = "全部账号分页")
    @GetMapping("/user/page")
    public R<AdminPageResult<Map<String, Object>>> userPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(service.userPage(pageNum, pageSize, keyword));
    }

    @Operation(summary = "全部家庭分页")
    @GetMapping("/family/page")
    public R<AdminPageResult<Map<String, Object>>> familyPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(service.familyPage(pageNum, pageSize, keyword));
    }

    @Operation(summary = "全部宝宝分页（可按账号 / 家庭筛选）")
    @GetMapping("/child/page")
    public R<AdminPageResult<Map<String, Object>>> childPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String familyId,
            @RequestParam(required = false) String keyword) {
        return R.ok(service.childPage(pageNum, pageSize, userId, familyId, keyword));
    }

    @Operation(summary = "记录明细分页（表白名单 + 账号 / 家庭 / 宝宝 / 时间筛选）")
    @GetMapping("/record/page")
    public R<AdminPageResult<Map<String, Object>>> recordPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String familyId,
            @RequestParam(required = false) String childId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return R.ok(service.recordPage(pageNum, pageSize, table, userId, familyId, childId, from, to));
    }

    @Operation(summary = "疫苗记录分页")
    @GetMapping("/vaccination/page")
    public R<AdminPageResult<Map<String, Object>>> vaccinationPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String childId,
            @RequestParam(required = false) String status) {
        return R.ok(service.vaccinationPage(pageNum, pageSize, userId, childId, status));
    }

    @Operation(summary = "提醒记录分页")
    @GetMapping("/reminder/page")
    public R<AdminPageResult<Map<String, Object>>> reminderPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String childId) {
        return R.ok(service.reminderPage(pageNum, pageSize, userId, childId));
    }
}
