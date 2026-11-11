package com.babylog.system.convert;

import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.domain.dto.UserSaveRequest;
import com.babylog.system.domain.entity.SysUser;
import com.babylog.system.domain.vo.UserVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 用户对象转换 */
@Mapper(componentModel = "spring")
public interface UserConvert {

    UserVO toVo(SysUser source);

    List<UserVO> toVoList(List<SysUser> source);

    SysUser toEntity(UserSaveRequest source);

    UserDTO toDto(SysUser source);

    List<UserDTO> toDtoList(List<SysUser> source);
}
