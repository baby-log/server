package com.babylog.biz.device.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.device.vo.DeviceVO;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备管理单测：列表映射与排序契约、踢出的归属校验与状态写入。
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    private static final long USER_ID = 42L;

    @Mock
    private BlDeviceMapper deviceMapper;

    @InjectMocks
    private DeviceServiceImpl service;

    @Test
    void list_mapsAllFieldsIncludingKickedRows() {
        BlDevice row = new BlDevice();
        row.setId(9001L);
        row.setUserId(USER_ID);
        row.setDeviceUuid("uuid-1");
        row.setDeviceName("Redmi 22101316UCP");
        row.setPlatform("ANDROID");
        row.setAppVersion("2.0.0");
        row.setStatus(BlDevice.STATUS_KICKED);
        row.setWorkerId(3);
        row.setLastActiveAt(LocalDateTime.of(2026, 11, 12, 20, 0));
        when(deviceMapper.selectList(any())).thenReturn(List.of(row));

        List<DeviceVO> vos = service.list(USER_ID);

        assertThat(vos).hasSize(1);
        DeviceVO vo = vos.get(0);
        assertThat(vo.getId()).isEqualTo(9001L);
        assertThat(vo.getDeviceUuid()).isEqualTo("uuid-1");
        assertThat(vo.getDeviceName()).isEqualTo("Redmi 22101316UCP");
        assertThat(vo.getPlatform()).isEqualTo("ANDROID");
        assertThat(vo.getAppVersion()).isEqualTo("2.0.0");
        assertThat(vo.getStatus()).isEqualTo("KICKED");
        assertThat(vo.getWorkerId()).isEqualTo(3);
        assertThat(vo.getLastActiveAt()).isEqualTo(
                LocalDateTime.of(2026, 11, 12, 20, 0)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    @Test
    void kick_marksStatusKicked() {
        BlDevice row = new BlDevice();
        row.setId(9001L);
        row.setUserId(USER_ID);
        row.setDeviceUuid("uuid-1");
        row.setStatus(BlDevice.STATUS_ACTIVE);
        when(deviceMapper.selectById(9001L)).thenReturn(row);

        service.kick(USER_ID, 9001L);

        ArgumentCaptor<BlDevice> captor = ArgumentCaptor.forClass(BlDevice.class);
        verify(deviceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BlDevice.STATUS_KICKED);
    }

    @Test
    void kick_rejectsForeignDeviceWithSameCodeAsMissing() {
        // 猜别人的设备 id 与不存在的 id 统一 8360：不给「这个 id 存不存在」的信号
        BlDevice foreign = new BlDevice();
        foreign.setId(9002L);
        foreign.setUserId(999L);
        when(deviceMapper.selectById(9002L)).thenReturn(foreign);

        assertThatThrownBy(() -> service.kick(USER_ID, 9002L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.DEVICE_NOT_FOUND.getCode());
        verify(deviceMapper, never()).updateById(any(BlDevice.class));
    }

    @Test
    void kick_rejectsMissingDevice() {
        when(deviceMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.kick(USER_ID, 404L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.DEVICE_NOT_FOUND.getCode());
    }
}
