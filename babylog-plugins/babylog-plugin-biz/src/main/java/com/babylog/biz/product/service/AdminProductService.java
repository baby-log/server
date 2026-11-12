package com.babylog.biz.product.service;

import com.babylog.biz.product.dto.ProductEditCmd;
import com.babylog.biz.product.dto.ProductPageQuery;
import com.babylog.biz.product.vo.ProductVO;
import com.babylog.common.core.page.PageResult;

/**
 * 管理端内置产品维护（M10）。
 *
 * <p>两通道隔离红线：只允许操作 {@code is_builtin=1}（id 1~99 预留段）的行——
 * 这些行由 base 通道下发、App 端推送一律 8345 拒绝；用户自建行（is_builtin=0）
 * 属 sync 通道，管理端不得触碰（8349）。
 *
 * <p>同步语义：每次维护<b>必须</b>让 {@code server_updated_at} 前进（游标）且
 * {@code version} +1（版本门禁），否则 App 端 base 通道永远拉不到这次改动。
 * 实现上：updateById 前把实体里加载出来的 serverUpdatedAt 置 null——
 * MP 默认 NOT_NULL 策略会跳过该列，数据库的 ON UPDATE CURRENT_TIMESTAMP(3)
 * 才有机会接管；version 靠乐观锁拦截器（实体 @Version 非空自动 SET version+1）。
 *
 * <p>墓碑行（deleted=1，如 id 91/92）被 @TableLogic 滤掉，selectById 返回 null →
 * 按 8348「产品不存在」处理。这正好守住「软删行不复活」：管理端无法把墓碑改回在售。
 */
public interface AdminProductService {

    /** 分页查询（固定 is_builtin=1） */
    PageResult<ProductVO> page(ProductPageQuery query);

    /**
     * 详情。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8348 产品不存在（含墓碑行）；8349 非内置产品
     */
    ProductVO get(long id);

    /**
     * 编辑（含上下架 = enabled 0/1）。
     *
     * @throws com.babylog.common.core.exception.ServiceException
     *         8348 产品不存在（含墓碑行）；8349 非内置产品不可通过管理端维护
     */
    void edit(long id, ProductEditCmd cmd);

    /**
     * 软删（置 deleted=1，App 端收到墓碑后 UI 隐藏该产品）。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8348 / 8349 同 {@link #edit}
     */
    void remove(long id);
}
