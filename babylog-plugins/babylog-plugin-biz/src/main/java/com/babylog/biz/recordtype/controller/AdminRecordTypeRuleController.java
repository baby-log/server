package com.babylog.biz.recordtype.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.babylog.biz.recordtype.dto.RecordTypeRuleEditCmd;
import com.babylog.biz.recordtype.dto.RecordTypeRulePageQuery;
import com.babylog.biz.recordtype.service.AdminRecordTypeRuleService;
import com.babylog.biz.recordtype.vo.RecordTypeRuleVO;
import com.babylog.common.core.page.PageResult;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端记录类型规则维护（sys-admin 体系，M41）。权限字符对应菜单 SQL V2.2.13 的 5050 树。
 *
 * <p><b>刻意只有分页/详情/编辑，没有新增与删除</b>——边界写死：云端只能调已有
 * 记录类型的默认开关、起止月龄、排序、分组、显示文案，不能云端新增记录类型
 * （防止膨胀成动态表单引擎，10 文档第八节）。新增类型永远走发版。
 */
@Tag(name = "BabyLog 记录类型规则（管理端）", description = "分页 / 详情 / 编辑（无新增：边界写死）")
@RestController
@RequestMapping("/babylog/record-type-rule")
public class AdminRecordTypeRuleController {

    private final AdminRecordTypeRuleService adminRecordTypeRuleService;

    public AdminRecordTypeRuleController(AdminRecordTypeRuleService adminRecordTypeRuleService) {
        this.adminRecordTypeRuleService = adminRecordTypeRuleService;
    }

    @Operation(summary = "规则分页（category/recordType 过滤，分类+排序升序）")
    @GetMapping("/page")
    @SaCheckPermission(value = "babylog:recordTypeRule:list", type = StpSysUtil.TYPE)
    public R<PageResult<RecordTypeRuleVO>> page(RecordTypeRulePageQuery query) {
        return R.ok(adminRecordTypeRuleService.page(query));
    }

    @Operation(summary = "规则详情")
    @GetMapping("/{id}")
    @SaCheckPermission(value = "babylog:recordTypeRule:query", type = StpSysUtil.TYPE)
    public R<RecordTypeRuleVO> get(@PathVariable("id") Long id) {
        return R.ok(adminRecordTypeRuleService.get(id));
    }

    @Operation(summary = "编辑规则（改动经 base 通道增量下发 App；recordType 不可改）")
    @PutMapping("/{id}")
    @SaCheckPermission(value = "babylog:recordTypeRule:edit", type = StpSysUtil.TYPE)
    public R<Void> edit(@PathVariable("id") Long id, @Validated @RequestBody RecordTypeRuleEditCmd cmd) {
        adminRecordTypeRuleService.edit(id, cmd);
        return R.ok();
    }
}
