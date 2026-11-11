// SysConfigMapper.java
package com.babylog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.system.domain.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/** 参数配置 Mapper */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
