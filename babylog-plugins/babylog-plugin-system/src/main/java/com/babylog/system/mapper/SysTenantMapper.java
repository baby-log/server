package com.babylog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.system.domain.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/** 租户 Mapper */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
