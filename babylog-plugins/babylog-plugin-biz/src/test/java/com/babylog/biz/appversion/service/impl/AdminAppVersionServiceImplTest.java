package com.babylog.biz.appversion.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.appversion.dto.AppVersionSaveCmd;
import com.babylog.biz.entity.BlAppVersion;
import com.babylog.biz.mapper.BlAppVersionMapper;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 管理端版本公告单测：DRAFT → PUBLISHED 单向状态机锁定 */
@ExtendWith(MockitoExtension.class)
class AdminAppVersionServiceImplTest {

    @Mock
    private BlAppVersionMapper versionMapper;

    @InjectMocks
    private AdminAppVersionServiceImpl service;

    private static AppVersionSaveCmd saveCmd() {
        AppVersionSaveCmd cmd = new AppVersionSaveCmd();
        cmd.setPlatform("ANDROID");
        cmd.setVersionCode(6);
        cmd.setVersionName("2.1.0");
        cmd.setForceUpdate(0);
        cmd.setDownloadUrl("https://example.com/app.apk");
        cmd.setNotice("修复若干问题");
        return cmd;
    }

    private static BlAppVersion row(String status) {
        BlAppVersion row = new BlAppVersion();
        row.setId(1L);
        row.setPlatform("ANDROID");
        row.setVersionCode(6);
        row.setVersionName("2.1.0");
        row.setForceUpdate(0);
        row.setStatus(status);
        return row;
    }

    @Test
    void create_landsAsDraft() {
        doAnswer(inv -> {
            ((BlAppVersion) inv.getArgument(0)).setId(123L);
            return 1;
        }).when(versionMapper).insert(any(BlAppVersion.class));

        Long id = service.create(saveCmd());

        assertThat(id).isEqualTo(123L);
        ArgumentCaptor<BlAppVersion> captor = ArgumentCaptor.forClass(BlAppVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getVersionCode()).isEqualTo(6);
    }

    @Test
    void update_allowsDraftOnly() {
        when(versionMapper.selectById(1L)).thenReturn(row("DRAFT"));

        service.update(1L, saveCmd());

        ArgumentCaptor<BlAppVersion> captor = ArgumentCaptor.forClass(BlAppVersion.class);
        verify(versionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersionName()).isEqualTo("2.1.0");
    }

    @Test
    void update_publish_remove_allRejectPublishedRow() {
        when(versionMapper.selectById(1L)).thenReturn(row("PUBLISHED"));

        Runnable[] actions = {
                () -> service.update(1L, saveCmd()),
                () -> service.publish(1L),
                () -> service.remove(1L)};
        for (Runnable action : actions) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(ServiceException.class)
                    .extracting(e -> ((ServiceException) e).getCode())
                    .isEqualTo(BabyLogErrorCode.APP_VERSION_NOT_DRAFT.getCode());
        }
        verify(versionMapper, never()).updateById(any(BlAppVersion.class));
        verify(versionMapper, never()).deleteById(anyLong());
    }

    @Test
    void publish_movesDraftToPublished() {
        when(versionMapper.selectById(1L)).thenReturn(row("DRAFT"));

        service.publish(1L);

        ArgumentCaptor<BlAppVersion> captor = ArgumentCaptor.forClass(BlAppVersion.class);
        verify(versionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void allOps_rejectMissingRow() {
        when(versionMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.APP_VERSION_NOT_FOUND.getCode());
        assertThatThrownBy(() -> service.update(404L, saveCmd()))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.publish(404L))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.remove(404L))
                .isInstanceOf(ServiceException.class);
    }
}
