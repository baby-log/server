package com.babylog.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.generator.domain.entity.GenTable;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生成表信息 Mapper
 */
@Mapper
public interface GenTableMapper extends BaseMapper<GenTable> {
}
