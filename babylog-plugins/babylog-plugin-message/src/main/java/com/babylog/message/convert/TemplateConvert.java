package com.babylog.message.convert;

import com.babylog.message.domain.dto.TemplateSaveRequest;
import com.babylog.message.domain.entity.MsgTemplate;
import com.babylog.message.domain.vo.TemplateVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 消息模板对象转换 */
@Mapper(componentModel = "spring")
public interface TemplateConvert {

    TemplateVO toVo(MsgTemplate source);

    List<TemplateVO> toVoList(List<MsgTemplate> source);

    MsgTemplate toEntity(TemplateSaveRequest source);
}
