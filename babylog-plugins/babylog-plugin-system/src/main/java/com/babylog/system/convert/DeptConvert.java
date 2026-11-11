package com.babylog.system.convert;

import com.babylog.system.api.dto.DeptDTO;
import com.babylog.system.domain.dto.DeptSaveRequest;
import com.babylog.system.domain.entity.SysDept;
import com.babylog.system.domain.vo.DeptVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 部门对象转换 */
@Mapper(componentModel = "spring")
public interface DeptConvert {

    DeptVO toVo(SysDept source);

    List<DeptVO> toVoList(List<SysDept> source);

    SysDept toEntity(DeptSaveRequest source);

    DeptDTO toDto(SysDept source);

    List<DeptDTO> toDtoList(List<SysDept> source);
}
