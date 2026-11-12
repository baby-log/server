package com.babylog.biz.appversion.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.appversion.dto.AppVersionPageQuery;
import com.babylog.biz.appversion.dto.AppVersionSaveCmd;
import com.babylog.biz.appversion.service.AdminAppVersionService;
import com.babylog.biz.appversion.vo.AppVersionVO;
import com.babylog.biz.entity.BlAppVersion;
import com.babylog.biz.mapper.BlAppVersionMapper;
import com.babylog.biz.support.PageUtils;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.common.core.page.PageResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 管理端版本公告实现 */
@Service
public class AdminAppVersionServiceImpl implements AdminAppVersionService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final BlAppVersionMapper versionMapper;

    public AdminAppVersionServiceImpl(BlAppVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    @Override
    public PageResult<AppVersionVO> page(AppVersionPageQuery query) {
        Page<BlAppVersion> page = versionMapper.selectPage(PageUtils.toMpPage(query),
                Wrappers.<BlAppVersion>lambdaQuery()
                        .eq(StringUtils.hasText(query.getPlatform()), BlAppVersion::getPlatform, query.getPlatform())
                        .eq(StringUtils.hasText(query.getStatus()), BlAppVersion::getStatus, query.getStatus())
                        .orderByDesc(BlAppVersion::getVersionCode)
                        .orderByDesc(BlAppVersion::getId));
        return PageUtils.toPageResult(page, page.getRecords().stream().map(AdminAppVersionServiceImpl::toVo).toList());
    }

    @Override
    public AppVersionVO get(long id) {
        return toVo(requireRow(id));
    }

    @Override
    public Long create(AppVersionSaveCmd cmd) {
        BlAppVersion row = new BlAppVersion();
        applyCmd(row, cmd);
        row.setStatus(STATUS_DRAFT);
        versionMapper.insert(row);
        return row.getId();
    }

    @Override
    public void update(long id, AppVersionSaveCmd cmd) {
        BlAppVersion row = requireDraft(id);
        applyCmd(row, cmd);
        versionMapper.updateById(row);
    }

    @Override
    public void publish(long id) {
        BlAppVersion row = requireDraft(id);
        row.setStatus(STATUS_PUBLISHED);
        versionMapper.updateById(row);
    }

    @Override
    public void remove(long id) {
        requireDraft(id);
        // @TableLogic 逻辑删除
        versionMapper.deleteById(id);
    }

    private BlAppVersion requireRow(long id) {
        BlAppVersion row = versionMapper.selectById(id);
        if (row == null) {
            throw new ServiceException(BabyLogErrorCode.APP_VERSION_NOT_FOUND);
        }
        return row;
    }

    private BlAppVersion requireDraft(long id) {
        BlAppVersion row = requireRow(id);
        if (!STATUS_DRAFT.equals(row.getStatus())) {
            throw new ServiceException(BabyLogErrorCode.APP_VERSION_NOT_DRAFT);
        }
        return row;
    }

    private static void applyCmd(BlAppVersion row, AppVersionSaveCmd cmd) {
        row.setPlatform(cmd.getPlatform());
        row.setVersionCode(cmd.getVersionCode());
        row.setVersionName(cmd.getVersionName());
        row.setForceUpdate(cmd.getForceUpdate());
        row.setDownloadUrl(cmd.getDownloadUrl());
        row.setNotice(cmd.getNotice());
    }

    private static AppVersionVO toVo(BlAppVersion row) {
        AppVersionVO vo = new AppVersionVO();
        vo.setId(row.getId());
        vo.setPlatform(row.getPlatform());
        vo.setVersionCode(row.getVersionCode());
        vo.setVersionName(row.getVersionName());
        vo.setForceUpdate(row.getForceUpdate());
        vo.setDownloadUrl(row.getDownloadUrl());
        vo.setNotice(row.getNotice());
        vo.setStatus(row.getStatus());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
