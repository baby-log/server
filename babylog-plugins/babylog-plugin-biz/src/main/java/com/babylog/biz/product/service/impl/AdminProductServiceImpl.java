package com.babylog.biz.product.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.entity.BlProduct;
import com.babylog.biz.mapper.BlProductMapper;
import com.babylog.biz.product.dto.ProductEditCmd;
import com.babylog.biz.product.dto.ProductPageQuery;
import com.babylog.biz.product.service.AdminProductService;
import com.babylog.biz.product.vo.ProductVO;
import com.babylog.biz.support.PageUtils;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.page.PageResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理端内置产品维护实现。
 *
 * <p>写路径细节（见接口 Javadoc）：编辑走「selectById 载入 → 改字段 →
 * 置空 serverUpdatedAt → updateById」。置空是关键——不置空的话 MP 会把
 * SELECT 出来的旧游标值显式 SET 回去，数据库的 ON UPDATE CURRENT_TIMESTAMP(3)
 * 不触发，App 端 base 通道（按 server_updated_at 增量）永远看不到这次改动。
 */
@Service
public class AdminProductServiceImpl implements AdminProductService {

    private static final int BUILTIN = 1;

    private final BlProductMapper productMapper;

    public AdminProductServiceImpl(BlProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public PageResult<ProductVO> page(ProductPageQuery query) {
        Page<BlProduct> page = productMapper.selectPage(PageUtils.toMpPage(query),
                Wrappers.<BlProduct>lambdaQuery()
                        .eq(BlProduct::getIsBuiltin, BUILTIN)
                        .eq(StringUtils.hasText(query.getCategory()), BlProduct::getCategory, query.getCategory())
                        .like(StringUtils.hasText(query.getBrand()), BlProduct::getBrand, query.getBrand())
                        .like(StringUtils.hasText(query.getName()), BlProduct::getName, query.getName())
                        .eq(query.getEnabled() != null, BlProduct::getEnabled, query.getEnabled())
                        .orderByAsc(BlProduct::getId));
        return PageUtils.toPageResult(page, page.getRecords().stream().map(AdminProductServiceImpl::toVo).toList());
    }

    @Override
    public ProductVO get(long id) {
        return toVo(requireBuiltin(id));
    }

    @Override
    public void edit(long id, ProductEditCmd cmd) {
        BlProduct row = requireBuiltin(id);
        row.setCategory(cmd.getCategory());
        row.setBrand(cmd.getBrand());
        row.setName(cmd.getName());
        row.setSpec(cmd.getSpec());
        row.setDefaultDose(cmd.getDefaultDose());
        row.setNote(cmd.getNote());
        row.setIconPath(cmd.getIconPath());
        row.setEnabled(cmd.getEnabled());
        // App 端业务时间戳口径：管理端维护也算一次「产品信息更新」
        row.setUpdatedAt(System.currentTimeMillis());
        // 游标列交给数据库 ON UPDATE 维护，实体必须置空（见类注释）
        row.setServerUpdatedAt(null);
        if (productMapper.updateById(row) == 0) {
            // 乐观锁冲突（并发维护）或行恰好被删——统一按不存在处理，重读重试即可
            throw new ServiceException(BabyLogErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Override
    public void remove(long id) {
        requireBuiltin(id);
        // @TableLogic 逻辑删除：置 deleted=1，墓碑经 base 通道下发后 App 端 UI 隐藏该产品
        productMapper.deleteById(id);
    }

    private BlProduct requireBuiltin(long id) {
        BlProduct row = productMapper.selectById(id);
        if (row == null) {
            // 含墓碑行（deleted=1 被 @TableLogic 滤掉）——「软删行不复活」由这里守住
            throw new ServiceException(BabyLogErrorCode.PRODUCT_NOT_FOUND);
        }
        if (row.getIsBuiltin() == null || row.getIsBuiltin() != BUILTIN) {
            // 用户自建产品属 sync 通道，管理端不得触碰（两通道隔离红线）
            throw new ServiceException(BabyLogErrorCode.PRODUCT_NOT_BUILTIN);
        }
        return row;
    }

    private static ProductVO toVo(BlProduct row) {
        ProductVO vo = new ProductVO();
        vo.setId(row.getId());
        vo.setCategory(row.getCategory());
        vo.setBrand(row.getBrand());
        vo.setName(row.getName());
        vo.setSpec(row.getSpec());
        vo.setDefaultDose(row.getDefaultDose());
        vo.setNote(row.getNote());
        vo.setIconPath(row.getIconPath());
        vo.setEnabled(row.getEnabled());
        vo.setIsBuiltin(row.getIsBuiltin());
        vo.setLastUsedAt(row.getLastUsedAt());
        vo.setVersion(row.getVersion());
        vo.setServerUpdatedAt(row.getServerUpdatedAt() == null
                ? null : java.sql.Timestamp.valueOf(row.getServerUpdatedAt()).getTime());
        return vo;
    }
}
