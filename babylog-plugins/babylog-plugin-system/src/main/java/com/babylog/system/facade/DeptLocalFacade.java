package com.babylog.system.facade;

import com.babylog.system.api.dto.DeptDTO;
import com.babylog.system.api.facade.IDeptFacade;
import com.babylog.system.convert.DeptConvert;
import com.babylog.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** 部门门面本地实现 */
@Component
@RequiredArgsConstructor
public class DeptLocalFacade implements IDeptFacade {

    private final DeptService deptService;
    private final DeptConvert deptConvert;

    @Override
    public DeptDTO getById(Long deptId) {
        return deptConvert.toDto(deptService.getById(deptId));
    }

    @Override
    public List<DeptDTO> listByIds(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        return deptConvert.toDtoList(deptService.listByIds(deptIds));
    }
}
