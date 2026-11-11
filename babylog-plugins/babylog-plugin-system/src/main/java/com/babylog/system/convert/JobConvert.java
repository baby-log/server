package com.babylog.system.convert;

import com.babylog.system.domain.dto.JobSaveRequest;
import com.babylog.system.domain.entity.SysJob;
import com.babylog.system.domain.vo.JobVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 定时任务对象转换 */
@Mapper(componentModel = "spring")
public interface JobConvert {

    JobVO toVo(SysJob source);

    List<JobVO> toVoList(List<SysJob> source);

    SysJob toEntity(JobSaveRequest source);
}
