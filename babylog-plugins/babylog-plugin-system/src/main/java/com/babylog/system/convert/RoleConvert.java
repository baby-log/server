package com.babylog.system.convert;

import com.babylog.system.api.dto.RoleDTO;
import com.babylog.system.domain.dto.RoleSaveRequest;
import com.babylog.system.domain.entity.SysRole;
import com.babylog.system.domain.vo.RoleVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 角色对象转换 */
@Mapper(componentModel = "spring")
public interface RoleConvert {

    RoleVO toVo(SysRole source);

    List<RoleVO> toVoList(List<SysRole> source);

    SysRole toEntity(RoleSaveRequest source);

    RoleDTO toDto(SysRole source);

    List<RoleDTO> toDtoList(List<SysRole> source);
}
