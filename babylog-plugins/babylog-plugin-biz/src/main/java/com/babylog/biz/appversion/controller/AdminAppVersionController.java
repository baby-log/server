package com.babylog.biz.appversion.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.babylog.biz.appversion.dto.AppVersionPageQuery;
import com.babylog.biz.appversion.dto.AppVersionSaveCmd;
import com.babylog.biz.appversion.service.AdminAppVersionService;
import com.babylog.biz.appversion.vo.AppVersionVO;
import com.babylog.common.core.page.PageResult;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import com.babylog.starter.web.annotation.RepeatSubmit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端版本公告（sys-admin 体系，M10）。权限字符对应菜单 SQL V2.2.9 的 5030 树。
 */
@Tag(name = "BabyLog 版本公告（管理端）", description = "分页 / 详情 / 新增 / 编辑 / 发布 / 删除")
@RestController
@RequestMapping("/babylog/version")
public class AdminAppVersionController {

    private final AdminAppVersionService adminAppVersionService;

    public AdminAppVersionController(AdminAppVersionService adminAppVersionService) {
        this.adminAppVersionService = adminAppVersionService;
    }

    @Operation(summary = "版本分页（platform/status 过滤，versionCode 倒序）")
    @GetMapping("/page")
    @SaCheckPermission(value = "babylog:version:list", type = StpSysUtil.TYPE)
    public R<PageResult<AppVersionVO>> page(AppVersionPageQuery query) {
        return R.ok(adminAppVersionService.page(query));
    }

    @Operation(summary = "版本详情")
    @GetMapping("/{id}")
    @SaCheckPermission(value = "babylog:version:query", type = StpSysUtil.TYPE)
    public R<AppVersionVO> get(@PathVariable("id") Long id) {
        return R.ok(adminAppVersionService.get(id));
    }

    @Operation(summary = "新增版本（落 DRAFT，发布走独立端点）")
    @PostMapping
    @RepeatSubmit
    @SaCheckPermission(value = "babylog:version:add", type = StpSysUtil.TYPE)
    public R<Long> create(@Validated @RequestBody AppVersionSaveCmd cmd) {
        return R.ok(adminAppVersionService.create(cmd));
    }

    @Operation(summary = "编辑版本（仅 DRAFT）")
    @PutMapping("/{id}")
    @SaCheckPermission(value = "babylog:version:edit", type = StpSysUtil.TYPE)
    public R<Void> update(@PathVariable("id") Long id, @Validated @RequestBody AppVersionSaveCmd cmd) {
        adminAppVersionService.update(id, cmd);
        return R.ok();
    }

    @Operation(summary = "发布版本（DRAFT → PUBLISHED，单向）")
    @PostMapping("/{id}/publish")
    @SaCheckPermission(value = "babylog:version:publish", type = StpSysUtil.TYPE)
    public R<Void> publish(@PathVariable("id") Long id) {
        adminAppVersionService.publish(id);
        return R.ok();
    }

    @Operation(summary = "删除版本（仅 DRAFT，逻辑删除）")
    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "babylog:version:remove", type = StpSysUtil.TYPE)
    public R<Void> remove(@PathVariable("id") Long id) {
        adminAppVersionService.remove(id);
        return R.ok();
    }
}
