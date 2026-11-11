package com.babylog.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.babylog.common.core.enums.CommonStatusEnum;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.page.PageResult;
import com.babylog.system.api.enums.SystemErrorCode;
import com.babylog.system.convert.DictDataConvert;
import com.babylog.system.domain.dto.DictDataQuery;
import com.babylog.system.domain.dto.DictDataSaveRequest;
import com.babylog.system.domain.entity.SysDictData;
import com.babylog.system.domain.entity.SysDictType;
import com.babylog.system.domain.vo.DictDataVO;
import com.babylog.system.mapper.SysDictDataMapper;
import com.babylog.system.mapper.SysDictTypeMapper;
import com.babylog.system.service.DictDataService;
import com.babylog.system.support.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/** 字典数据服务实现 */
@Service
@RequiredArgsConstructor
public class DictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements DictDataService {

    private final DictDataConvert dictDataConvert;
    private final SysDictTypeMapper dictTypeMapper;

    @Override
    public PageResult<DictDataVO> pageData(DictDataQuery query) {
        Page<SysDictData> page = page(PageUtils.toMpPage(query), Wrappers.<SysDictData>lambdaQuery()
                .eq(StringUtils.hasText(query.getDictType()), SysDictData::getDictType, query.getDictType())
                .like(StringUtils.hasText(query.getDictLabel()), SysDictData::getDictLabel, query.getDictLabel())
                .eq(query.getStatus() != null, SysDictData::getStatus, query.getStatus())
                .orderByAsc(SysDictData::getSort));
        return PageUtils.toPageResult(page, dictDataConvert.toVoList(page.getRecords()));
    }

    @Override
    public DictDataVO getData(Long dictCode) {
        return dictDataConvert.toVo(requireData(dictCode));
    }

    @Override
    public Long createData(DictDataSaveRequest request) {
        checkTypeExists(request.getDictType());
        SysDictData entity = dictDataConvert.toEntity(request);
        entity.setId(null);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateData(DictDataSaveRequest request) {
        requireData(request.getId());
        checkTypeExists(request.getDictType());
        updateById(dictDataConvert.toEntity(request));
    }

    @Override
    public void deleteData(Long dictCode) {
        requireData(dictCode);
        removeById(dictCode);
    }

    @Override
    public List<SysDictData> listEnabledByType(String dictType) {
        return list(Wrappers.<SysDictData>lambdaQuery()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, CommonStatusEnum.ENABLED.getValue())
                .orderByAsc(SysDictData::getSort));
    }

    private SysDictData requireData(Long dictCode) {
        SysDictData data = getById(dictCode);
        if (data == null) {
            throw new ServiceException(SystemErrorCode.DICT_DATA_NOT_FOUND);
        }
        return data;
    }

    /** 字典数据必须挂在已存在的字典类型下 */
    private void checkTypeExists(String dictType) {
        long count = dictTypeMapper.selectCount(Wrappers.<SysDictType>lambdaQuery()
                .eq(SysDictType::getDictType, dictType));
        if (count == 0) {
            throw new ServiceException(SystemErrorCode.DICT_TYPE_NOT_FOUND);
        }
    }
}
