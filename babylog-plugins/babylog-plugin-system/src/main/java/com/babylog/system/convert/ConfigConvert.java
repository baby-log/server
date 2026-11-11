package com.babylog.system.convert;

import com.babylog.system.domain.dto.ConfigSaveRequest;
import com.babylog.system.domain.entity.SysConfig;
import com.babylog.system.domain.vo.ConfigVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 参数配置对象转换 */
@Mapper(componentModel = "spring")
public interface ConfigConvert {

    ConfigVO toVo(SysConfig source);

    List<ConfigVO> toVoList(List<SysConfig> source);

    SysConfig toEntity(ConfigSaveRequest source);
}
