package com.babylog.docsync.adapter.impl;

import com.babylog.docsync.api.enums.DocSyncTypeEnum;
import com.babylog.docsync.entity.DocSyncConfig;
import org.springframework.stereotype.Component;

/**
 * Eolink 平台适配器（手动同步）
 *
 * <p>Eolink 为商业 SaaS 产品，开放 API 待验证，暂仅提供 OpenAPI JSON 导出和管理后台跳转。
 */
@Component
public class EolinkManualAdapter extends AbstractManualSyncAdapter {

    @Override
    public DocSyncTypeEnum getType() {
        return DocSyncTypeEnum.EOLINK;
    }

    @Override
    public String getManagementUrl(DocSyncConfig config) {
        return config.getServerUrl();
    }
}
