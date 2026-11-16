package com.babylog.biz.devicebind.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.babylog.biz.devicebind.dto.DeviceBindUserCodeCmd;
import com.babylog.biz.devicebind.service.DeviceBindService;
import com.babylog.biz.devicebind.vo.DeviceBindStatusVO;
import com.babylog.common.core.result.R;
import com.babylog.starter.auth.account.StpAppUtil;
import com.babylog.starter.core.context.LoginContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BabyLogDeviceBindControllerTest {

    @Test
    void confirmTakesIdentityOnlyFromLoginContext() {
        DeviceBindService service = mock(DeviceBindService.class);
        BabyLogDeviceBindController controller = new BabyLogDeviceBindController(service);
        DeviceBindUserCodeCmd cmd = new DeviceBindUserCodeCmd();
        cmd.setUserCode("012345");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(service.confirm(eq(9007199254740993L), eq(cmd), eq("127.0.0.1")))
                .thenReturn(new DeviceBindStatusVO("approved"));

        try (MockedStatic<LoginContext> context = mockStatic(LoginContext.class)) {
            context.when(LoginContext::getUserId).thenReturn(9007199254740993L);
            R<DeviceBindStatusVO> result = controller.confirm(cmd, request);
            assertThat(result.getData().getStatus()).isEqualTo("approved");
        }

        verify(service).confirm(9007199254740993L, cmd, "127.0.0.1");
    }

    @Test
    void mobileCommandsHaveNoUserIdAndRequireAppUserLogin() throws Exception {
        assertThat(Arrays.stream(DeviceBindUserCodeCmd.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain("userId");
        for (String name : new String[]{"confirm", "deny"}) {
            Method method = BabyLogDeviceBindController.class.getDeclaredMethod(
                    name, DeviceBindUserCodeCmd.class, HttpServletRequest.class);
            SaCheckLogin login = method.getAnnotation(SaCheckLogin.class);
            assertThat(login).as(name + " 鉴权").isNotNull();
            assertThat(login.type()).isEqualTo(StpAppUtil.TYPE);
        }
        for (String name : new String[]{"apply", "poll"}) {
            Method method = "apply".equals(name)
                    ? BabyLogDeviceBindController.class.getDeclaredMethod(name,
                    com.babylog.biz.devicebind.dto.DeviceBindApplyCmd.class, HttpServletRequest.class)
                    : BabyLogDeviceBindController.class.getDeclaredMethod(name,
                    com.babylog.biz.devicebind.dto.DeviceBindPollCmd.class);
            assertThat(method.getAnnotation(SaCheckLogin.class)).isNull();
        }
    }
}
