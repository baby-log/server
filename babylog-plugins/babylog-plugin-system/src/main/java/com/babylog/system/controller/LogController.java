package com.babylog.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.babylog.common.core.page.PageResult;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import com.babylog.system.domain.dto.LoginLogQuery;
import com.babylog.system.domain.dto.OperLogQuery;
import com.babylog.system.domain.vo.LoginLogVO;
import com.babylog.system.domain.vo.OperLogVO;
import com.babylog.system.service.LoginLogService;
import com.babylog.system.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 日志管理（登录日志 + 操作日志，查询只读） */
@Tag(name = "日志管理", description = "登录日志 + 操作日志查询")
@RestController
@RequestMapping("/system/log")
@RequiredArgsConstructor
public class LogController {

    private final LoginLogService loginLogService;
    private final OperLogService operLogService;

    /** 登录日志分页 */
    @Operation(summary = "登录日志分页")
    @GetMapping("/login/page")
    @SaCheckPermission(value = "system:loginlog:list", type = StpSysUtil.TYPE)
    public R<PageResult<LoginLogVO>> loginLogPage(LoginLogQuery query) {
        return R.ok(loginLogService.pageLogs(query));
    }

    /** 操作日志分页 */
    @Operation(summary = "操作日志分页")
    @GetMapping("/oper/page")
    @SaCheckPermission(value = "system:operlog:list", type = StpSysUtil.TYPE)
    public R<PageResult<OperLogVO>> operLogPage(OperLogQuery query) {
        return R.ok(operLogService.pageLogs(query));
    }
}
