package com.babylog.biz.appversion.service;

import com.babylog.biz.appversion.dto.AppVersionPageQuery;
import com.babylog.biz.appversion.dto.AppVersionSaveCmd;
import com.babylog.biz.appversion.vo.AppVersionVO;
import com.babylog.common.core.page.PageResult;

/**
 * 管理端版本公告（bl_app_version，M10）。
 *
 * <p>状态机：DRAFT →（发布）→ PUBLISHED，单向。已发布的行<b>锁定</b>
 * （改/删/再发布一律 8391）——用户可能已经看到过这版公告，改历史会破坏
 * 「同一 versionCode 对外口径稳定」的预期；要改内容就发新 versionCode。
 *
 * <p>App 端升级检查端点（拉最新 PUBLISHED 行）属 V3 升级提醒功能，本批不做。
 */
public interface AdminAppVersionService {

    /** 分页（平台/状态过滤，versionCode 倒序） */
    PageResult<AppVersionVO> page(AppVersionPageQuery query);

    /**
     * 详情。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8390 版本记录不存在
     */
    AppVersionVO get(long id);

    /**
     * 新增（落 DRAFT）。
     *
     * @return 新记录 id
     */
    Long create(AppVersionSaveCmd cmd);

    /**
     * 编辑（仅 DRAFT）。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8390 不存在；8391 已发布锁定
     */
    void update(long id, AppVersionSaveCmd cmd);

    /**
     * 发布（DRAFT → PUBLISHED，单向）。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8390 不存在；8391 已发布锁定
     */
    void publish(long id);

    /**
     * 删除（仅 DRAFT；逻辑删除）。
     *
     * @throws com.babylog.common.core.exception.ServiceException 8390 不存在；8391 已发布锁定
     */
    void remove(long id);
}
