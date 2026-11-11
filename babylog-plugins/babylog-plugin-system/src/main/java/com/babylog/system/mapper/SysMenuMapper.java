package com.babylog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.babylog.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 菜单 Mapper */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

}
