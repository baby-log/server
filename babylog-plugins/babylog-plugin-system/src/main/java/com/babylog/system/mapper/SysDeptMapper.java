// SysDeptMapper.java
package com.babylog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.system.domain.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;

/** 部门 Mapper */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {
}
