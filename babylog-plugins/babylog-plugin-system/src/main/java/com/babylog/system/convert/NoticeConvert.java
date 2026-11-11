package com.babylog.system.convert;

import com.babylog.system.domain.dto.NoticeSaveRequest;
import com.babylog.system.domain.entity.SysNotice;
import com.babylog.system.domain.vo.NoticeVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 通知公告对象转换 */
@Mapper(componentModel = "spring")
public interface NoticeConvert {

    NoticeVO toVo(SysNotice source);

    List<NoticeVO> toVoList(List<SysNotice> source);

    SysNotice toEntity(NoticeSaveRequest source);
}
