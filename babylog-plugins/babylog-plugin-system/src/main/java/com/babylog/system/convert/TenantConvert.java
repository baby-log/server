package com.babylog.system.convert;

import com.babylog.system.domain.dto.TenantSaveRequest;
import com.babylog.system.domain.entity.SysTenant;
import com.babylog.system.domain.vo.TenantVO;
import org.mapstruct.Mapper;

import java.util.List;

/** 租户对象转换（packageName/accountCount 为 VO 扩展字段，服务层手工填充） */
@Mapper(componentModel = "spring")
public interface TenantConvert {

    TenantVO toVo(SysTenant source);

    List<TenantVO> toVoList(List<SysTenant> source);

    SysTenant toEntity(TenantSaveRequest source);
}
