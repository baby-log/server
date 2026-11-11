package com.babylog.docsync.adapter.impl;

import com.babylog.docsync.api.enums.DocSyncTypeEnum;
import com.babylog.docsync.entity.DocSyncConfig;
import org.springframework.stereotype.Component;

/**
 * XXL-API 平台适配器（手动同步）
 *
 * <p>XXL-API 官方未提供任何开放 API 或编程式接口，所有文档操作必须通过 Web 管理界面手动完成。
 * 本适配器仅提供 OpenAPI JSON 导出和管理后台跳转。
 */
@Component
public class XxlApiManualAdapter extends AbstractManualSyncAdapter {

    @Override
    public DocSyncTypeEnum getType() {
        return DocSyncTypeEnum.XXL_API;
    }

    @Override
    public String getManagementUrl(DocSyncConfig config) {
        return config.getServerUrl();
    }
}
