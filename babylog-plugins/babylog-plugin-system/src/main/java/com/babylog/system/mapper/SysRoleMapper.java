package com.babylog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.system.domain.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 角色 Mapper */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
