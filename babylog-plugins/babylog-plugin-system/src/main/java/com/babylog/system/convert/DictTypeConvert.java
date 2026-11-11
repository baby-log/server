package com.babylog.system.convert;

import com.babylog.system.domain.dto.DictTypeSaveRequest;
import com.babylog.system.domain.entity.SysDictType;
import com.babylog.system.domain.vo.DictTypeVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 字典类型对象转换 */
@Mapper(componentModel = "spring")
public interface DictTypeConvert {

    DictTypeVO toVo(SysDictType source);

    List<DictTypeVO> toVoList(List<SysDictType> source);

    SysDictType toEntity(DictTypeSaveRequest source);
}
