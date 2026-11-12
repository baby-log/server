package com.babylog.biz.device.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.device.service.DeviceService;
import com.babylog.biz.device.vo.AdminDeviceOverviewVO;
import com.babylog.biz.device.vo.DeviceVO;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.api.facade.IUserAccountFacade;
import com.babylog.system.api.facade.IUserFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端设备查看单测：账号解析口径（用户名优先、邮箱兜底）、踢出复用 M8 DeviceService。
 */
@ExtendWith(MockitoExtension.class)
class AdminDeviceServiceImplTest {

    @Mock
    private IUserFacade userFacade;

    @Mock
    private IUserAccountFacade userAccountFacade;

    @Mock
    private DeviceService deviceService;

    @Mock
    private BlDeviceMapper deviceMapper;

    @InjectMocks
    private AdminDeviceServiceImpl service;

    private static UserDTO user(long id, String username, String email) {
        UserDTO dto = new UserDTO();
        dto.setId(id);
        dto.setUsername(username);
        dto.setEmail(email);
        dto.setStatus(0);
        return dto;
    }

    @Test
    void overview_resolvesByUsernameFirst() {
        when(userFacade.getByUsername("m9sa01")).thenReturn(user(42L, "m9sa01", "a@163.com"));
        when(deviceService.list(42L)).thenReturn(List.of(new DeviceVO()));

        AdminDeviceOverviewVO vo = service.overview("m9sa01");

        assertThat(vo.getUserId()).isEqualTo(42L);
        assertThat(vo.getUsername()).isEqualTo("m9sa01");
        assertThat(vo.getDevices()).hasSize(1);
        verify(userAccountFacade, never()).getByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void overview_fallsBackToEmailWithNormalization() {
        when(userFacade.getByUsername("A@163.com")).thenReturn(null);
        when(userAccountFacade.getByEmail("a@163.com")).thenReturn(user(42L, "m9sa01", "a@163.com"));

        AdminDeviceOverviewVO vo = service.overview(" A@163.com ");

        assertThat(vo.getUserId()).isEqualTo(42L);
        // 邮箱兜底必须 trim + 小写归一化（与 AppAuthServiceImpl 口径一致）
        verify(userAccountFacade).getByEmail("a@163.com");
    }

    @Test
    void overview_rejectsUnknownAndBlankAccount() {
        when(userFacade.getByUsername("ghost")).thenReturn(null);
        when(userAccountFacade.getByEmail("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.overview("ghost"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.ACCOUNT_NOT_FOUND.getCode());
        assertThatThrownBy(() -> service.overview("  "))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.ACCOUNT_NOT_FOUND.getCode());
    }

    @Test
    void kick_delegatesToM8ServiceWithOwnerUserId() {
        BlDevice row = new BlDevice();
        row.setId(9001L);
        row.setUserId(42L);
        when(deviceMapper.selectById(9001L)).thenReturn(row);

        service.kick(9001L);

        // 复用 M8 语义：KICKED + 拦 refresh + 重新登录可复活，与本人踢出同一条路径
        verify(deviceService).kick(42L, 9001L);
    }

    @Test
    void kick_rejectsMissingDevice() {
        when(deviceMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.kick(404L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(BabyLogErrorCode.DEVICE_NOT_FOUND.getCode());
        verify(deviceService, never()).kick(anyLong(), anyLong());
    }
}
