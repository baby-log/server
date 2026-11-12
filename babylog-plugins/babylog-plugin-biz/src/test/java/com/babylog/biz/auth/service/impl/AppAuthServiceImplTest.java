package com.babylog.biz.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.biz.auth.dto.DeviceInfo;
import com.babylog.biz.auth.dto.EmailCodeCmd;
import com.babylog.biz.auth.dto.EmailLoginCmd;
import com.babylog.biz.auth.dto.PasswordLoginCmd;
import com.babylog.biz.auth.dto.RegisterCmd;
import com.babylog.biz.auth.dto.RefreshTokenCmd;
import com.babylog.biz.auth.service.AppTokenService;
import com.babylog.biz.auth.service.EmailCodeService;
import com.babylog.biz.auth.store.RefreshTokenRecord;
import com.babylog.biz.auth.store.RefreshTokenStore;
import com.babylog.biz.auth.support.SysUserAccountDao;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.biz.auth.vo.LoginTokenVO;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.enums.SystemErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证编排单测：三条登录路（验证码/账密/refresh）的建档、校验、双 token 与设备落行。
 *
 * <p>DAO / RefreshToken 存储 / Sa-Token 全部 Mock——不连库、不连 Redis、不碰请求上下文。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppAuthServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long NEW_USER_ID = 2002L;
    private static final String RAW_PASSWORD = "secret-password";

    @Mock
    private EmailCodeService emailCodeService;
    @Mock
    private AppTokenService tokenService;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private SysUserAccountDao userAccountDao;
    @Mock
    private BlDeviceMapper deviceMapper;

    private BabyLogAuthProperties properties;
    private AppAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new BabyLogAuthProperties();
        service = new AppAuthServiceImpl(emailCodeService, tokenService, refreshTokenStore,
                userAccountDao, deviceMapper, properties);

        when(emailCodeService.normalize(anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class).trim().toLowerCase(Locale.ROOT));
        when(tokenService.issueAccessToken(anyLong(), anyString())).thenReturn("access-token");
        when(tokenService.accessTtlSeconds()).thenReturn(7200L);
        when(refreshTokenStore.issue(anyLong(), anyString(), any())).thenReturn("refresh-token");
        when(deviceMapper.selectList(any())).thenReturn(List.of());
        doAnswer(inv -> {
            BlDevice row = inv.getArgument(0);
            row.setId(9001L);
            return 1;
        }).when(deviceMapper).insert(any(BlDevice.class));
    }

    // ---------------- 邮箱验证码登录 ----------------

    @Test
    void loginByEmailCode_createsAccountOnFirstLogin() {
        when(userAccountDao.findByEmail("mom@example.com")).thenReturn(Optional.empty());
        when(userAccountDao.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userAccountDao.insert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(NEW_USER_ID);

        LoginTokenVO vo = service.loginByEmailCode(emailLoginCmd(" Mom@Example.com ", "123456", device()));

        verify(emailCodeService).verify("mom@example.com", "123456");
        assertThat(vo.isNewUser()).isTrue();
        assertThat(vo.getUserId()).isEqualTo(NEW_USER_ID);
        assertThat(vo.getAccessToken()).isEqualTo("access-token");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(vo.getAccessExpiresIn()).isEqualTo(7200L);
        assertThat(vo.getRefreshExpiresIn()).isEqualTo(properties.getRefreshTokenTtlSeconds());
        assertThat(vo.getDeviceId()).isEqualTo(9001L);
        // 自动建档的账号：邮箱落在 email 列，用户名是随机串（不是邮箱本身）
        ArgumentCaptor<String> username = ArgumentCaptor.forClass(String.class);
        verify(userAccountDao).insert(username.capture(), anyString(), eq("mom@example.com"), anyString());
        assertThat(username.getValue()).startsWith("bl_").isNotEqualTo("mom@example.com");
    }

    @Test
    void loginByEmailCode_reusesExistingAccount() {
        when(userAccountDao.findByEmail("mom@example.com"))
                .thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "")));

        LoginTokenVO vo = service.loginByEmailCode(emailLoginCmd("mom@example.com", "123456", null));

        assertThat(vo.isNewUser()).isFalse();
        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        verify(userAccountDao, never()).insert(anyString(), anyString(), anyString(), anyString());
        // 没上报设备：不落设备行，也不阻断登录
        assertThat(vo.getDeviceId()).isNull();
        verify(deviceMapper, never()).insert(any(BlDevice.class));
    }

    @Test
    void loginByEmailCode_rejectsDisabledAccount() {
        when(userAccountDao.findByEmail("mom@example.com"))
                .thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "", 1)));

        assertBizError(() -> service.loginByEmailCode(emailLoginCmd("mom@example.com", "123456", null)),
                SystemErrorCode.USER_DISABLED);
    }

    @Test
    void sendLoginCode_delegatesToEmailCodeService() {
        CodeSendVO expected = new CodeSendVO();
        expected.setExpireSeconds(300L);
        when(emailCodeService.sendLoginCode("mom@example.com")).thenReturn(expected);
        EmailCodeCmd cmd = new EmailCodeCmd();
        cmd.setEmail("mom@example.com");

        assertThat(service.sendLoginCode(cmd)).isSameAs(expected);
    }

    // ---------------- 账密注册 ----------------

    @Test
    void register_rejectsDuplicateUsername() {
        when(userAccountDao.findByUsername("mom")).thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "")));

        assertBizError(() -> service.register(registerCmd("mom", RAW_PASSWORD, "")),
                SystemErrorCode.USERNAME_EXISTS);
        verify(userAccountDao, never()).insert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void register_rejectsEmailAlreadyBound() {
        when(userAccountDao.findByUsername("mom")).thenReturn(Optional.empty());
        when(userAccountDao.findByEmail("mom@example.com"))
                .thenReturn(Optional.of(row(USER_ID, "other", "别人", "mom@example.com")));

        assertBizError(() -> service.register(registerCmd("mom", RAW_PASSWORD, "Mom@Example.com")),
                BabyLogErrorCode.EMAIL_ALREADY_USED);
    }

    @Test
    void register_hashesPasswordAndLogsIn() {
        when(userAccountDao.findByUsername("mom")).thenReturn(Optional.empty());
        when(userAccountDao.insert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(NEW_USER_ID);

        LoginTokenVO vo = service.register(registerCmd("mom", RAW_PASSWORD, ""));

        assertThat(vo.getUserId()).isEqualTo(NEW_USER_ID);
        assertThat(vo.isNewUser()).isTrue();
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(userAccountDao).insert(eq("mom"), eq("mom"), eq(""), hash.capture());
        // 落库必须是 BCrypt 哈希，且能校验回原文
        assertThat(hash.getValue()).isNotEqualTo(RAW_PASSWORD).startsWith("$2");
        assertThat(BCrypt.checkpw(RAW_PASSWORD, hash.getValue())).isTrue();
    }

    // ---------------- 账密登录 ----------------

    @Test
    void loginByPassword_acceptsUsername() {
        when(userAccountDao.findByUsername("mom"))
                .thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "", 0, BCrypt.hashpw(RAW_PASSWORD))));

        LoginTokenVO vo = service.loginByPassword(passwordCmd("mom"));

        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        assertThat(vo.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void loginByPassword_acceptsEmailAsAccount() {
        when(userAccountDao.findByEmail("mom@example.com"))
                .thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "mom@example.com", 0, BCrypt.hashpw(RAW_PASSWORD))));

        LoginTokenVO vo = service.loginByPassword(passwordCmd("Mom@Example.com"));

        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        verify(userAccountDao, never()).findByUsername(anyString());
    }

    @Test
    void loginByPassword_rejectsWrongPasswordWithLoginFailed() {
        when(userAccountDao.findByUsername("mom"))
                .thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "", 0, BCrypt.hashpw(RAW_PASSWORD))));

        assertBizError(() -> service.loginByPassword(passwordCmd("mom", "wrong-password")),
                SystemErrorCode.LOGIN_FAILED);
    }

    @Test
    void loginByPassword_rejectsUnknownAccountWithLoginFailed() {
        when(userAccountDao.findByUsername("ghost")).thenReturn(Optional.empty());

        // 账号不存在与密码错误同码，不泄露账号是否存在
        assertBizError(() -> service.loginByPassword(passwordCmd("ghost")), SystemErrorCode.LOGIN_FAILED);
    }

    // ---------------- refresh ----------------

    @Test
    void refresh_rejectsUnknownRefreshToken() {
        when(refreshTokenStore.resolve("bad")).thenReturn(Optional.empty());
        RefreshTokenCmd cmd = new RefreshTokenCmd();
        cmd.setRefreshToken("bad");

        assertBizError(() -> service.refresh(cmd), BabyLogErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_issuesNewAccessToken() {
        when(refreshTokenStore.resolve("refresh-token"))
                .thenReturn(Optional.of(new RefreshTokenRecord(USER_ID, "device-1", 1234L)));
        when(userAccountDao.findById(USER_ID)).thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "")));

        RefreshTokenCmd cmd = new RefreshTokenCmd();
        cmd.setRefreshToken("refresh-token");
        LoginTokenVO vo = service.refresh(cmd);

        assertThat(vo.getAccessToken()).isEqualTo("access-token");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(vo.getRefreshExpiresIn()).isEqualTo(1234L);
        assertThat(vo.getDeviceId()).isEqualTo(9001L);
    }

    @Test
    void refresh_revokesTokenWhenAccountDisabled() {
        when(refreshTokenStore.resolve("refresh-token"))
                .thenReturn(Optional.of(new RefreshTokenRecord(USER_ID, "device-1", 1234L)));
        when(userAccountDao.findById(USER_ID))
                .thenReturn(Optional.of(row(USER_ID, "mom", "妈妈", "", 1)));

        RefreshTokenCmd cmd = new RefreshTokenCmd();
        cmd.setRefreshToken("refresh-token");

        assertBizError(() -> service.refresh(cmd), SystemErrorCode.USER_DISABLED);
        // 停用后不许旧 refresh 无限续命
        verify(refreshTokenStore).revoke("refresh-token");
    }

    @Test
    void logout_revokesRefreshTokenAndCurrentAccess() {
        service.logout("refresh-token");

        verify(refreshTokenStore).revoke("refresh-token");
        verify(tokenService).logoutCurrent();
    }

    // ---------------- 辅助 ----------------

    private static EmailLoginCmd emailLoginCmd(String email, String code, DeviceInfo device) {
        EmailLoginCmd cmd = new EmailLoginCmd();
        cmd.setEmail(email);
        cmd.setCode(code);
        cmd.setDevice(device);
        return cmd;
    }

    private static RegisterCmd registerCmd(String username, String password, String email) {
        RegisterCmd cmd = new RegisterCmd();
        cmd.setUsername(username);
        cmd.setPassword(password);
        cmd.setEmail(email);
        return cmd;
    }

    private static PasswordLoginCmd passwordCmd(String account) {
        return passwordCmd(account, RAW_PASSWORD);
    }

    private static PasswordLoginCmd passwordCmd(String account, String password) {
        PasswordLoginCmd cmd = new PasswordLoginCmd();
        cmd.setAccount(account);
        cmd.setPassword(password);
        return cmd;
    }

    private static DeviceInfo device() {
        DeviceInfo device = new DeviceInfo();
        device.setDeviceUuid("device-1");
        device.setDeviceName("Pixel 8");
        device.setPlatform("android");
        device.setAppVersion("2.0.0");
        return device;
    }

    private static SysUserAccountDao.SysUserRow row(Long id, String username, String nickname, String email) {
        return row(id, username, nickname, email, 0, "");
    }

    private static SysUserAccountDao.SysUserRow row(Long id, String username, String nickname,
                                                    String email, int status) {
        return row(id, username, nickname, email, status, "");
    }

    private static SysUserAccountDao.SysUserRow row(Long id, String username, String nickname,
                                                    String email, int status, String password) {
        return new SysUserAccountDao.SysUserRow(id, username, nickname, password, email, status);
    }

    private static void assertBizError(Runnable action, com.babylog.common.core.enums.error.ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(expected.getCode()));
    }
}
