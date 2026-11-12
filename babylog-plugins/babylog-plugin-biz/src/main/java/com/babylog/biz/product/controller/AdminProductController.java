package com.babylog.biz.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.babylog.biz.product.dto.ProductEditCmd;
import com.babylog.biz.product.dto.ProductPageQuery;
import com.babylog.biz.product.service.AdminProductService;
import com.babylog.biz.product.vo.ProductVO;
import com.babylog.common.core.page.PageResult;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpSysUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端内置产品维护（sys-admin 体系，M10）。
 *
 * <p>与 App 端点（/app/babylog/**，app-user 体系）分属两套鉴权：
 * 本类全部走 {@code @SaCheckPermission(type = StpSysUtil.TYPE)}，
 * 权限字符与菜单 SQL（V2.2.9 的 5010 树）一一对应。
 */
@Tag(name = "BabyLog 内置产品维护（管理端）", description = "查询 / 编辑（含上下架）/ 软删")
@RestController
@RequestMapping("/babylog/product")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @Operation(summary = "内置产品分页（固定 is_builtin=1）")
    @GetMapping("/page")
    @SaCheckPermission(value = "babylog:product:list", type = StpSysUtil.TYPE)
    public R<PageResult<ProductVO>> page(ProductPageQuery query) {
        return R.ok(adminProductService.page(query));
    }

    @Operation(summary = "内置产品详情")
    @GetMapping("/{id}")
    @SaCheckPermission(value = "babylog:product:query", type = StpSysUtil.TYPE)
    public R<ProductVO> get(@PathVariable("id") Long id) {
        return R.ok(adminProductService.get(id));
    }

    @Operation(summary = "编辑内置产品（含上下架；改动经 base 通道增量下发 App）")
    @PutMapping("/{id}")
    @SaCheckPermission(value = "babylog:product:edit", type = StpSysUtil.TYPE)
    public R<Void> edit(@PathVariable("id") Long id, @Validated @RequestBody ProductEditCmd cmd) {
        adminProductService.edit(id, cmd);
        return R.ok();
    }

    @Operation(summary = "软删内置产品（置墓碑，App 端 UI 隐藏；不可复活）")
    @DeleteMapping("/{id}")
    @SaCheckPermission(value = "babylog:product:remove", type = StpSysUtil.TYPE)
    public R<Void> remove(@PathVariable("id") Long id) {
        adminProductService.remove(id);
        return R.ok();
    }
}
