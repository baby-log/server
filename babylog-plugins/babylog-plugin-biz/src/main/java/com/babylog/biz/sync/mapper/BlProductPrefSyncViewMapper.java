package com.babylog.biz.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.biz.sync.entity.BlProductPrefSyncView;
import org.apache.ibatis.annotations.Mapper;

/**
 * bl_product_prefs 同步通道视图实体的 Mapper（M67，口径同 {@link BlProductSyncViewMapper}）：
 * 同步通道一律走本视图 Mapper（无逻辑删除，deleted 全量可见），零手写 SQL。
 */
@Mapper
public interface BlProductPrefSyncViewMapper extends BaseMapper<BlProductPrefSyncView> {
}
