package com.babylog.biz.kb.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.kb.dto.KbFaqSaveCmd;
import com.babylog.biz.kb.service.AdminKbFaqService;
import com.babylog.biz.kb.vo.KbFaqVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
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
 * 管理端 · 育儿知识库 FAQ 维护（K6-S / M76）。
 *
 * <p>与既有管理端 controller 同口径：路径 {@code /babylog/**}（项目惯例无 {@code /admin} 前缀）、
 * 类级 {@code @SaCheckLogin(type = StpSysUtil.TYPE)}（满足 P0 ArchUnit A11，且不新增
 * {@code @SaCheckPermission} 权限字符——权限字符须与菜单 SQL 逐字对应，本节点按 K3 先例
 * 只做登录态校验，菜单权限配套登记在遗留清单）。
 *
 * <p><b>红线一</b>：保存时 {@code source} 必填（{@code 8465}）、正文限 2000 字，
 * 禁整篇搬运；ui 管理端「参数设置」页另行维护 {@code babylog.ai.quota.kb_qa.limit}。
 */
@Tag(name = "BabyLog 育儿知识库 FAQ 维护（管理端）", description = "条目列表 / 新增编辑 / 删除（来源必填）")
@RestController
@RequestMapping("/babylog/kb/faq")
@SaCheckLogin(type = StpSysUtil.TYPE)
public class AdminKbFaqController {

    private final AdminKbFaqService faqService;

    public AdminKbFaqController(AdminKbFaqService faqService) {
        this.faqService = faqService;
    }

    @Operation(summary = "FAQ 列表（含下架条目，管理端可见）")
    @GetMapping("/list")
    public R<List<KbFaqVO>> list(@RequestParam(value = "category", required = false) String category,
                                 @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(faqService.list(category, keyword));
    }

    @Operation(summary = "新增 / 编辑 FAQ（来源必填；正文限 2000 字，禁整篇搬运）")
    @PostMapping("/save")
    public R<KbFaqVO> save(@RequestBody KbFaqSaveCmd cmd) {
        return R.ok(faqService.save(cmd));
    }

    @Operation(summary = "删除 FAQ（逻辑删条目 + 物理清收藏关联）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        if (id == null) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID);
        }
        faqService.delete(id);
        return R.ok(null);
    }
}
