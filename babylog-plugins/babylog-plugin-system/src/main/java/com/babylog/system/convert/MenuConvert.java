package com.babylog.system.convert;

import com.babylog.system.domain.dto.MenuSaveRequest;
import com.babylog.system.domain.entity.SysMenu;
import com.babylog.system.domain.vo.MenuVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 菜单对象转换 */
@Mapper(componentModel = "spring")
public interface MenuConvert {

    MenuVO toVo(SysMenu source);

    List<MenuVO> toVoList(List<SysMenu> source);

    SysMenu toEntity(MenuSaveRequest source);
}
