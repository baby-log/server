package com.babylog.system.convert;

import com.babylog.system.domain.dto.PostSaveRequest;
import com.babylog.system.domain.entity.SysPost;
import com.babylog.system.domain.vo.PostVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 岗位对象转换 */
@Mapper(componentModel = "spring")
public interface PostConvert {

    PostVO toVo(SysPost source);

    List<PostVO> toVoList(List<SysPost> source);

    SysPost toEntity(PostSaveRequest source);
}
