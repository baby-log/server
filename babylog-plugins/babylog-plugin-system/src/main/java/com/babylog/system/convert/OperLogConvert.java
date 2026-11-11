package com.babylog.system.convert;

import com.babylog.system.domain.entity.SysOperLog;
import com.babylog.system.domain.vo.OperLogVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 操作日志对象转换 */
@Mapper(componentModel = "spring")
public interface OperLogConvert {

    OperLogVO toVo(SysOperLog source);

    List<OperLogVO> toVoList(List<SysOperLog> source);
}
