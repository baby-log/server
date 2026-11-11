package com.babylog.system.convert;

import com.babylog.system.api.dto.DictDataDTO;
import com.babylog.system.domain.dto.DictDataSaveRequest;
import com.babylog.system.domain.entity.SysDictData;
import com.babylog.system.domain.vo.DictDataVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 字典数据对象转换 */
@Mapper(componentModel = "spring")
public interface DictDataConvert {

    DictDataVO toVo(SysDictData source);

    List<DictDataVO> toVoList(List<SysDictData> source);

    SysDictData toEntity(DictDataSaveRequest source);

    DictDataDTO toDto(SysDictData source);

    List<DictDataDTO> toDtoList(List<SysDictData> source);
}
