package com.babylog.biz.auth.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.biz.auth.dto.DeviceInfo;
import com.babylog.biz.auth.dto.EmailCodeCmd;
import com.babylog.biz.auth.dto.EmailLoginCmd;
import com.babylog.biz.auth.dto.MiniLoginCmd;
import com.babylog.biz.auth.dto.PasswordLoginCmd;
import com.babylog.biz.auth.dto.RefreshTokenCmd;
import com.babylog.biz.auth.dto.RegisterCmd;
import com.babylog.biz.auth.service.AppTokenService;
import com.babylog.biz.auth.service.EmailCodeService;
import com.babylog.biz.auth.store.RefreshTokenRecord;
import com.babylog.biz.auth.store.RefreshTokenStore;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.biz.auth.vo.LoginTokenVO;
import com.babylog.biz.device.service.DeviceWorkerIdAllocator;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.dto.SocialBindingDTO;
import com.babylog.system.api.dto.UserAccountCreateCmd;
import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.api.dto.WechatSessionDTO;
import com.babylog.system.api.enums.SystemErrorCode;
import com.babylog.system.api.facade.IUserAccountFacade;
import com.babylog.system.api.facade.IUserFacade;
import com.babylog.system.api.facade.ISocialAccountFacade;
import com.babylog.system.api.facade.IWechatMiniFacade;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证编排单测：三条登录路（验证码/账密/refresh）的建档、校验、双 token 与设备落行。
 *
 * <p>账号读写走 system 的两道契约（{@link IUserFacade} 只读 + {@link IUserAccountFacade} 写），
 * 这里连同 RefreshToken 存储 / Sa-Token 一起 Mock——不连库、不连 Redis、不碰请求上下文。
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
    private IUserFacade userFacade;
    @Mock
    private IUserAccountFacade userAccountFacade;
    @Mock
    private IWechatMiniFacade wechatMiniFacade;
    @Mock
    private ISocialAccountFacade socialAccountFacade;
    @Mock
    private BlDeviceMapper deviceMapper;
    @Mock
    private DeviceWorkerIdAllocator workerIdAllocator;

    private BabyLogAuthProperties properties;
    private AppAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new BabyLogAuthProperties();
        service = new AppAuthServiceImpl(emailCodeService, tokenService, refreshTokenStore,
                userFacade, userAccountFacade, wechatMiniFacade, socialAccountFacade,
                deviceMapper, workerIdAllocator, properties);

        when(emailCodeService.normalize(anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class).trim().toLowerCase(Locale.ROOT));
        when(tokenService.issueAccessToken(anyLong(), anyString())).thenReturn("access-token");
        when(tokenService.accessTtlSeconds()).thenReturn(7200L);
        when(refreshTokenStore.issue(anyLong(), anyString(), any())).thenReturn("refresh-token");
        when(deviceMapper.selectList(any())).thenReturn(List.of());
        when(workerIdAllocator.nextWorkerId(anyLong())).thenReturn(0);
        doAnswer(inv -> {
            BlDevice row = inv.getArgument(0);
            row.setId(9001L);
            return 1;
        }).when(deviceMapper).insert(any(BlDevice.class));
    }

    // ---------------- 邮箱验证码登录 ----------------

    @Test
    void loginByEmailCode_createsAccountOnFirstLogin() {
        when(userAccountFacade.getByEmail("mom@example.com")).thenReturn(null);
        when(userAccountFacade.existsByUsername(anyString())).thenReturn(false);
        when(userAccountFacade.createAccount(any())).thenReturn(NEW_USER_ID);
        when(userFacade.getById(NEW_USER_ID)).thenReturn(user(NEW_USER_ID, "bl_abc123", "妈妈", "mom@example.com"));

        LoginTokenVO vo = service.loginByEmailCode(emailLoginCmd(" Mom@Example.com ", "123456", device()));

        verify(emailCodeService).verify("mom@example.com", "123456");
        assertThat(vo.isNewUser()).isTrue();
        assertThat(vo.getUserId()).isEqualTo(NEW_USER_ID);
        assertThat(vo.getAccessToken()).isEqualTo("access-token");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(vo.getAccessExpiresIn()).isEqualTo(7200L);
        assertThat(vo.getRefreshExpiresIn()).isEqualTo(properties.getRefreshTokenTtlSeconds());
        assertThat(vo.getDeviceId()).isEqualTo(9001L);
        // 新设备落行时同步分配 workerId（App 拿它生成雪花 ID），随登录响应回传
        assertThat(vo.getWorkerId()).isZero();
        verify(workerIdAllocator).nextWorkerId(NEW_USER_ID);
        // 自动建档的账号：邮箱落在 email 列，用户名是随机串（不是邮箱本身），密码留空由契约生成随机串
        ArgumentCaptor<UserAccountCreateCmd> cmd = ArgumentCaptor.forClass(UserAccountCreateCmd.class);
        verify(userAccountFacade).createAccount(cmd.capture());
        assertThat(cmd.getValue().getUsername()).startsWith("bl_").isNotEqualTo("mom@example.com");
        assertThat(cmd.getValue().getEmail()).isEqualTo("mom@example.com");
        assertThat(cmd.getValue().getRawPassword()).isNull();
    }

    @Test
    void loginByEmailCode_reusesExistingAccount() {
        when(userAccountFacade.getByEmail("mom@example.com"))
                .thenReturn(user(USER_ID, "mom", "妈妈", "mom@example.com"));

        LoginTokenVO vo = service.loginByEmailCode(emailLoginCmd("mom@example.com", "123456", null));

        assertThat(vo.isNewUser()).isFalse();
        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        verify(userAccountFacade, never()).createAccount(any());
        // 没上报设备：不落设备行，也不阻断登录
        assertThat(vo.getDeviceId()).isNull();
        verify(deviceMapper, never()).insert(any(BlDevice.class));
    }

    @Test
    void loginByEmailCode_assignsWorkerIdToLegacyDeviceRow() {
        when(userAccountFacade.getByEmail("mom@example.com"))
                .thenReturn(user(USER_ID, "mom", "妈妈", "mom@example.com"));
        // V2.2.7 之前落的设备行没有 workerId：登录时补一个，但不重复插行、不改行 id
        BlDevice legacy = new BlDevice();
        legacy.setId(7001L);
        legacy.setUserId(USER_ID);
        legacy.setDeviceUuid("device-1");
        legacy.setStatus("ACTIVE");
        when(deviceMapper.selectList(any())).thenReturn(List.of(legacy));
        when(workerIdAllocator.nextWorkerId(USER_ID)).thenReturn(5);

        LoginTokenVO vo = service.loginByEmailCode(emailLoginCmd("mom@example.com", "123456", device()));

        assertThat(vo.getDeviceId()).isEqualTo(7001L);
        assertThat(vo.getWorkerId()).isEqualTo(5);
        verify(deviceMapper, never()).insert(any(BlDevice.class));
        verify(deviceMapper).updateById(legacy);
    }

    @Test
    void loginByEmailCode_rejectsDisabledAccount() {
        when(userAccountFacade.getByEmail("mom@example.com"))
                .thenReturn(user(USER_ID, "mom", "妈妈", "mom@example.com", 1));

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
        when(userAccountFacade.existsByUsername("mom")).thenReturn(true);

        assertBizError(() -> service.register(registerCmd("mom", RAW_PASSWORD, "")),
                BabyLogErrorCode.USERNAME_ALREADY_USED);
        verify(userAccountFacade, never()).createAccount(any());
    }

    @Test
    void register_rejectsEmailAlreadyBound() {
        when(userAccountFacade.existsByUsername("mom")).thenReturn(false);
        when(userAccountFacade.existsByEmail("mom@example.com")).thenReturn(true);

        assertBizError(() -> service.register(registerCmd("mom", RAW_PASSWORD, "Mom@Example.com")),
                BabyLogErrorCode.EMAIL_ALREADY_USED);
        verify(userAccountFacade, never()).createAccount(any());
    }

    @Test
    void register_passesRawPasswordToContract() {
        when(userAccountFacade.existsByUsername("mom")).thenReturn(false);
        when(userAccountFacade.createAccount(any())).thenReturn(NEW_USER_ID);
        when(userFacade.getById(NEW_USER_ID)).thenReturn(user(NEW_USER_ID, "mom", "mom", ""));

        LoginTokenVO vo = service.register(registerCmd("mom", RAW_PASSWORD, ""));

        assertThat(vo.getUserId()).isEqualTo(NEW_USER_ID);
        assertThat(vo.isNewUser()).isTrue();
        ArgumentCaptor<UserAccountCreateCmd> cmd = ArgumentCaptor.forClass(UserAccountCreateCmd.class);
        verify(userAccountFacade).createAccount(cmd.capture());
        // 哈希由 system 侧负责（BCrypt）；biz 只传原文，不自己算——两处各算一遍才容易出现口径漂移
        assertThat(cmd.getValue().getUsername()).isEqualTo("mom");
        assertThat(cmd.getValue().getNickname()).isEqualTo("mom");
        assertThat(cmd.getValue().getRawPassword()).isEqualTo(RAW_PASSWORD);
    }

    @Test
    void register_translatesUpstreamUsernameConflictToBizCode() {
        when(userAccountFacade.existsByUsername("mom")).thenReturn(false);
        when(userAccountFacade.createAccount(any()))
                .thenThrow(new ServiceException(SystemErrorCode.USERNAME_EXISTS));

        // 并发撞库：上游抛 2003，biz 出口必须是 8378（App 只认 8xxx 段）
        assertBizError(() -> service.register(registerCmd("mom", RAW_PASSWORD, "")),
                BabyLogErrorCode.USERNAME_ALREADY_USED);
    }

    @Test
    void register_translatesUpstreamEmailConflictToBizCode() {
        when(userAccountFacade.existsByUsername("mom")).thenReturn(false);
        when(userAccountFacade.createAccount(any()))
                .thenThrow(new ServiceException(SystemErrorCode.EMAIL_ALREADY_USED));

        assertBizError(() -> service.register(registerCmd("mom", RAW_PASSWORD, "")),
                BabyLogErrorCode.EMAIL_ALREADY_USED);
    }

    @Test
    void register_failsWhenAccountUnreadableAfterCreate() {
        when(userAccountFacade.existsByUsername("mom")).thenReturn(false);
        when(userAccountFacade.createAccount(any())).thenReturn(NEW_USER_ID);
        when(userFacade.getById(NEW_USER_ID)).thenReturn(null);

        // 建号成功却读不到：不发明文 token 给一个不存在的身份
        assertThatThrownBy(() -> service.register(registerCmd("mom", RAW_PASSWORD, "")))
                .isInstanceOf(ServiceException.class);
    }

    // ---------------- 账密登录 ----------------

    @Test
    void loginByPassword_acceptsUsername() {
        when(userFacade.getByUsername("mom")).thenReturn(user(USER_ID, "mom", "妈妈", ""));
        when(userAccountFacade.checkPassword(USER_ID, RAW_PASSWORD)).thenReturn(true);

        LoginTokenVO vo = service.loginByPassword(passwordCmd("mom"));

        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        assertThat(vo.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void loginByPassword_acceptsEmailAsAccount() {
        when(userAccountFacade.getByEmail("mom@example.com"))
                .thenReturn(user(USER_ID, "mom", "妈妈", "mom@example.com"));
        when(userAccountFacade.checkPassword(USER_ID, RAW_PASSWORD)).thenReturn(true);

        LoginTokenVO vo = service.loginByPassword(passwordCmd("Mom@Example.com"));

        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        verify(userFacade, never()).getByUsername(anyString());
    }

    @Test
    void loginByPassword_rejectsWrongPasswordWithLoginFailed() {
        when(userFacade.getByUsername("mom")).thenReturn(user(USER_ID, "mom", "妈妈", ""));
        when(userAccountFacade.checkPassword(USER_ID, "wrong-password")).thenReturn(false);

        assertBizError(() -> service.loginByPassword(passwordCmd("mom", "wrong-password")),
                SystemErrorCode.LOGIN_FAILED);
    }

    @Test
    void loginByPassword_rejectsUnknownAccountWithLoginFailed() {
        when(userFacade.getByUsername("ghost")).thenReturn(null);

        // 账号不存在与密码错误同码，不泄露账号是否存在
        assertBizError(() -> service.loginByPassword(passwordCmd("ghost")), SystemErrorCode.LOGIN_FAILED);
        verify(userAccountFacade, never()).checkPassword(anyLong(), anyString());
    }

    @Test
    void loginByPassword_rejectsDisabledAccount() {
        when(userFacade.getByUsername("mom")).thenReturn(user(USER_ID, "mom", "妈妈", "", 1));
        when(userAccountFacade.checkPassword(USER_ID, RAW_PASSWORD)).thenReturn(true);

        assertBizError(() -> service.loginByPassword(passwordCmd("mom")), SystemErrorCode.USER_DISABLED);
    }

    // ---------------- 微信小程序登录（M20） ----------------

    @Test
    void loginByWxMini_createsAccountAndBindingOnFirstLogin() {
        when(wechatMiniFacade.code2Session("babylog", "js-code")).thenReturn(wxSession("open-1", null));
        when(socialAccountFacade.findByChannelOpenId("wechat-mini", "open-1")).thenReturn(null);
        when(userAccountFacade.existsByUsername(anyString())).thenReturn(false);
        when(userAccountFacade.createAccount(any())).thenReturn(NEW_USER_ID);
        when(userFacade.getById(NEW_USER_ID)).thenReturn(user(NEW_USER_ID, "wx_abc123", "微信用户", ""));

        LoginTokenVO vo = service.loginByWxMini(miniLoginCmd("js-code", device()));

        assertThat(vo.isNewUser()).isTrue();
        assertThat(vo.getUserId()).isEqualTo(NEW_USER_ID);
        assertThat(vo.getAccessToken()).isEqualTo("access-token");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(vo.getDeviceId()).isEqualTo(9001L);
        // 首登建档：wx_ 前缀随机用户名 + 昵称「微信用户」+ 无邮箱 + 密码留空（仅能靠微信身份登录）
        ArgumentCaptor<UserAccountCreateCmd> cmd = ArgumentCaptor.forClass(UserAccountCreateCmd.class);
        verify(userAccountFacade).createAccount(cmd.capture());
        assertThat(cmd.getValue().getUsername()).startsWith("wx_");
        assertThat(cmd.getValue().getNickname()).isEqualTo("微信用户");
        assertThat(cmd.getValue().getEmail()).isEmpty();
        assertThat(cmd.getValue().getRawPassword()).isNull();
        // 绑定建立：userId + channel + openId + unionId
        verify(socialAccountFacade).bind(NEW_USER_ID, "wechat-mini", "open-1", null);
    }

    @Test
    void loginByWxMini_firstLogin_bindsUnionIdWhenPresent() {
        when(wechatMiniFacade.code2Session("babylog", "js-code")).thenReturn(wxSession("open-1", "union-1"));
        when(socialAccountFacade.findByChannelOpenId("wechat-mini", "open-1")).thenReturn(null);
        when(userAccountFacade.existsByUsername(anyString())).thenReturn(false);
        when(userAccountFacade.createAccount(any())).thenReturn(NEW_USER_ID);
        when(userFacade.getById(NEW_USER_ID)).thenReturn(user(NEW_USER_ID, "wx_abc123", "微信用户", ""));

        service.loginByWxMini(miniLoginCmd("js-code", null));

        verify(socialAccountFacade).bind(NEW_USER_ID, "wechat-mini", "open-1", "union-1");
    }

    @Test
    void loginByWxMini_reusesBindingOnSubsequentLogin() {
        when(wechatMiniFacade.code2Session("babylog", "js-code")).thenReturn(wxSession("open-1", "union-1"));
        when(socialAccountFacade.findByChannelOpenId("wechat-mini", "open-1"))
                .thenReturn(binding(USER_ID, "open-1", null));
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "wx_abc123", "微信用户", ""));

        LoginTokenVO vo = service.loginByWxMini(miniLoginCmd("js-code", null));

        assertThat(vo.isNewUser()).isFalse();
        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        verify(userAccountFacade, never()).createAccount(any());
        verify(socialAccountFacade, never()).bind(anyLong(), anyString(), anyString(), anyString());
        // unionId 后补：绑定行缺 unionId，本次登录拿到了就补齐
        verify(socialAccountFacade).updateUnionId("wechat-mini", "open-1", "union-1");
    }

    @Test
    void loginByWxMini_skipsUnionIdBackfillWhenUnchanged() {
        when(wechatMiniFacade.code2Session("babylog", "js-code")).thenReturn(wxSession("open-1", "union-1"));
        when(socialAccountFacade.findByChannelOpenId("wechat-mini", "open-1"))
                .thenReturn(binding(USER_ID, "open-1", "union-1"));
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "wx_abc123", "微信用户", ""));

        service.loginByWxMini(miniLoginCmd("js-code", null));

        verify(socialAccountFacade, never()).updateUnionId(anyString(), anyString(), anyString());
    }

    @Test
    void loginByWxMini_rejectsDisabledBoundAccount() {
        when(wechatMiniFacade.code2Session("babylog", "js-code")).thenReturn(wxSession("open-1", null));
        when(socialAccountFacade.findByChannelOpenId("wechat-mini", "open-1"))
                .thenReturn(binding(USER_ID, "open-1", null));
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "wx_abc123", "微信用户", "", 1));

        assertBizError(() -> service.loginByWxMini(miniLoginCmd("js-code", null)),
                SystemErrorCode.USER_DISABLED);
    }

    @Test
    void loginByWxMini_rejectsOrphanBinding() {
        when(wechatMiniFacade.code2Session("babylog", "js-code")).thenReturn(wxSession("open-1", null));
        when(socialAccountFacade.findByChannelOpenId("wechat-mini", "open-1"))
                .thenReturn(binding(USER_ID, "open-1", null));
        when(userFacade.getById(USER_ID)).thenReturn(null);

        assertBizError(() -> service.loginByWxMini(miniLoginCmd("js-code", null)),
                BabyLogErrorCode.WX_MINI_BOUND_CONFLICT);
        verify(userAccountFacade, never()).createAccount(any());
    }

    @Test
    void loginByWxMini_translateNotConfigured() {
        when(wechatMiniFacade.code2Session("babylog", "js-code"))
                .thenThrow(new ServiceException(SystemErrorCode.SOCIAL_NOT_CONFIGURED));

        assertBizError(() -> service.loginByWxMini(miniLoginCmd("js-code", null)),
                BabyLogErrorCode.WX_MINI_NOT_CONFIGURED);
        verify(socialAccountFacade, never()).findByChannelOpenId(anyString(), anyString());
    }

    @Test
    void loginByWxMini_translateInvalidCode() {
        when(wechatMiniFacade.code2Session("babylog", "js-code"))
                .thenThrow(new ServiceException(SystemErrorCode.SOCIAL_CODE_INVALID));

        assertBizError(() -> service.loginByWxMini(miniLoginCmd("js-code", null)),
                BabyLogErrorCode.WX_CODE_INVALID);
    }

    @Test
    void loginByWxMini_translateBindRaceConflict() {
        when(wechatMiniFacade.code2Session("babylog", "js-code")).thenReturn(wxSession("open-1", null));
        when(socialAccountFacade.findByChannelOpenId("wechat-mini", "open-1")).thenReturn(null);
        when(userAccountFacade.existsByUsername(anyString())).thenReturn(false);
        when(userAccountFacade.createAccount(any())).thenReturn(NEW_USER_ID);
        when(userFacade.getById(NEW_USER_ID)).thenReturn(user(NEW_USER_ID, "wx_abc123", "微信用户", ""));
        // find 与 bind 之间的并发窗口：同一 openId 被另一请求抢先绑定
        doThrow(new ServiceException(SystemErrorCode.SOCIAL_ALREADY_BOUND))
                .when(socialAccountFacade).bind(anyLong(), anyString(), anyString(), any());

        assertBizError(() -> service.loginByWxMini(miniLoginCmd("js-code", null)),
                BabyLogErrorCode.WX_MINI_BOUND_CONFLICT);
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
    void refresh_rejectsWhenAccountGone() {
        when(refreshTokenStore.resolve("refresh-token"))
                .thenReturn(Optional.of(new RefreshTokenRecord(USER_ID, "device-1", 1234L)));
        when(userFacade.getById(USER_ID)).thenReturn(null);

        RefreshTokenCmd cmd = new RefreshTokenCmd();
        cmd.setRefreshToken("refresh-token");

        assertBizError(() -> service.refresh(cmd), BabyLogErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_issuesNewAccessToken() {
        when(refreshTokenStore.resolve("refresh-token"))
                .thenReturn(Optional.of(new RefreshTokenRecord(USER_ID, "device-1", 1234L)));
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "mom", "妈妈", ""));

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
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "mom", "妈妈", "", 1));

        RefreshTokenCmd cmd = new RefreshTokenCmd();
        cmd.setRefreshToken("refresh-token");

        assertBizError(() -> service.refresh(cmd), SystemErrorCode.USER_DISABLED);
        // 停用后不许旧 refresh 无限续命
        verify(refreshTokenStore).revoke("refresh-token");
    }

    // ---------------- 电视设备码绑定登录（M45） ----------------

    @Test
    void loginByBoundDevice_reusesTokenDeviceAndWorkerIdPipeline() {
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "mom", "妈妈", ""));
        DeviceInfo tv = device();
        tv.setDeviceUuid("living-room-tv");
        tv.setDeviceName("客厅电视");
        tv.setPlatform("android_tv");

        LoginTokenVO vo = service.loginByBoundDevice(USER_ID, tv);

        assertThat(vo.getAccessToken()).isEqualTo("access-token");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(vo.getDeviceId()).isEqualTo(9001L);
        assertThat(vo.getWorkerId()).isZero();
        assertThat(vo.isNewUser()).isFalse();
        ArgumentCaptor<BlDevice> inserted = ArgumentCaptor.forClass(BlDevice.class);
        verify(deviceMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getDeviceUuid()).isEqualTo("living-room-tv");
        assertThat(inserted.getValue().getPlatform()).isEqualTo("ANDROID_TV");
        verify(userAccountFacade, never()).createAccount(any());
    }

    @Test
    void loginByBoundDevice_rejectsDisabledAccount() {
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "mom", "妈妈", "", 1));

        assertBizError(() -> service.loginByBoundDevice(USER_ID, device()), SystemErrorCode.USER_DISABLED);
        verify(tokenService, never()).issueAccessToken(any(), any());
    }

    @Test
    void loginByBoundDevice_rejectsMissingAccount() {
        when(userFacade.getById(USER_ID)).thenReturn(null);

        assertBizError(() -> service.loginByBoundDevice(USER_ID, device()),
                BabyLogErrorCode.DEVICE_BIND_CODE_INVALID);
        verify(tokenService, never()).issueAccessToken(any(), any());
    }

    @Test
    void logout_revokesRefreshTokenAndCurrentAccess() {
        service.logout("refresh-token");

        verify(refreshTokenStore).revoke("refresh-token");
        verify(tokenService).logoutCurrent();
    }

    // ---------------- refresh 拦截被踢设备（M8 设备管理） ----------------

    @Test
    void refresh_rejectsKickedDeviceAndRevokesToken() {
        when(refreshTokenStore.resolve("refresh-token"))
                .thenReturn(Optional.of(new RefreshTokenRecord(USER_ID, "device-1", 1234L)));
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "mom", "妈妈", ""));
        BlDevice kicked = new BlDevice();
        kicked.setId(9001L);
        kicked.setUserId(USER_ID);
        kicked.setDeviceUuid("device-1");
        kicked.setStatus(BlDevice.STATUS_KICKED);
        when(deviceMapper.selectList(any())).thenReturn(List.of(kicked));

        RefreshTokenCmd cmd = new RefreshTokenCmd();
        cmd.setRefreshToken("refresh-token");

        // 8376 而不是新错误码：App 端 AuthRepository 只对 8376/1002 走「清凭证」分支
        assertBizError(() -> service.refresh(cmd), BabyLogErrorCode.REFRESH_TOKEN_INVALID);
        verify(refreshTokenStore).revoke("refresh-token");
        verify(tokenService, never()).issueAccessToken(any(), any());
    }

    @Test
    void refresh_allowsWhenDeviceRowMissing() {
        // refresh 记录先于设备行存在（历史数据）：查不到行不算被踢，放行
        when(refreshTokenStore.resolve("refresh-token"))
                .thenReturn(Optional.of(new RefreshTokenRecord(USER_ID, "device-9", 1234L)));
        when(userFacade.getById(USER_ID)).thenReturn(user(USER_ID, "mom", "妈妈", ""));
        when(deviceMapper.selectList(any())).thenReturn(List.of());

        RefreshTokenCmd cmd = new RefreshTokenCmd();
        cmd.setRefreshToken("refresh-token");
        LoginTokenVO vo = service.refresh(cmd);

        assertThat(vo.getAccessToken()).isEqualTo("access-token");
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
    private static WechatSessionDTO wxSession(String openId, String unionId) {
        WechatSessionDTO dto = new WechatSessionDTO();
        dto.setOpenId(openId);
        dto.setUnionId(unionId);
        return dto;
    }
    private static SocialBindingDTO binding(Long userId, String openId, String unionId) {
        SocialBindingDTO dto = new SocialBindingDTO();
        dto.setUserId(userId);
        dto.setChannel("wechat-mini");
        dto.setOpenId(openId);
        dto.setUnionId(unionId);
        return dto;
    }
    private static MiniLoginCmd miniLoginCmd(String code, DeviceInfo device) {
        MiniLoginCmd cmd = new MiniLoginCmd();
        cmd.setCode(code);
        cmd.setDevice(device);
        return cmd;
    }

    private static UserDTO user(Long id, String username, String nickname, String email) {
        return user(id, username, nickname, email, 0);
    }

    private static UserDTO user(Long id, String username, String nickname, String email, int status) {
        UserDTO dto = new UserDTO();
        dto.setId(id);
        dto.setUsername(username);
        dto.setNickname(nickname);
        dto.setEmail(email);
        dto.setStatus(status);
        return dto;
    }

    private static void assertBizError(Runnable action, com.babylog.common.core.enums.error.ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(expected.getCode()));
    }
}
