package com.babylog.biz.device.service;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * workerId 分配单测：号段从 0 开始、按 max+1 递增、用尽时拒绝。
 */
@ExtendWith(MockitoExtension.class)
class DeviceWorkerIdAllocatorTest {

    private static final long USER_ID = 1001L;

    @Mock
    private BlDeviceMapper deviceMapper;

    @Test
    void firstDeviceGetsZero() {
        when(deviceMapper.selectMaxWorkerId(USER_ID)).thenReturn(-1);

        assertThat(new DeviceWorkerIdAllocator(deviceMapper).nextWorkerId(USER_ID)).isZero();
    }

    @Test
    void nextDeviceGetsMaxPlusOne() {
        when(deviceMapper.selectMaxWorkerId(USER_ID)).thenReturn(6);

        assertThat(new DeviceWorkerIdAllocator(deviceMapper).nextWorkerId(USER_ID)).isEqualTo(7);
    }

    @Test
    void lastAvailableIdIsStillIssued() {
        when(deviceMapper.selectMaxWorkerId(USER_ID)).thenReturn(DeviceWorkerIdAllocator.MAX_WORKER_ID - 1);

        assertThat(new DeviceWorkerIdAllocator(deviceMapper).nextWorkerId(USER_ID))
                .isEqualTo(DeviceWorkerIdAllocator.MAX_WORKER_ID);
    }

    @Test
    void exhaustionIsRejected() {
        when(deviceMapper.selectMaxWorkerId(USER_ID)).thenReturn(DeviceWorkerIdAllocator.MAX_WORKER_ID);

        assertThatThrownBy(() -> new DeviceWorkerIdAllocator(deviceMapper).nextWorkerId(USER_ID))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BabyLogErrorCode.DEVICE_LIMIT_EXCEEDED.getCode()));
    }
}
