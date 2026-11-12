package com.babylog.biz.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.biz.auth.dto.DeviceInfo;
import com.babylog.biz.auth.dto.EmailCodeCmd;
import com.babylog.biz.auth.dto.EmailLoginCmd;
import com.babylog.biz.auth.dto.PasswordLoginCmd;
import com.babylog.biz.auth.dto.RegisterCmd;
import com.babylog.biz.auth.dto.RefreshTokenCmd;
import com.babylog.biz.auth.service.AppAuthService;
import com.babylog.biz.auth.service.AppTokenService;
import com.babylog.biz.auth.service.EmailCodeService;
import com.babylog.biz.auth.store.RefreshTokenRecord;
import com.babylog.biz.auth.store.RefreshTokenStore;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.biz.auth.vo.LoginTokenVO;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.dto.UserAccountCreateCmd;
import com.babylog.system.api.dto.UserDTO;
import com.babylog.system.api.enums.SystemErrorCode;
import com.babylog.system.api.facade.IUserAccountFacade;
import com.babylog.system.api.facade.IUserFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * App 端认证编排实现。
 *
 * <p>账号体系口径：**复用 {@code sys_user} + 独立的 app-user Token 体系**——不新建用户表
 * （M4 拍板），PC 端与 App 端互不干扰（Token/踢登隔离）。
 *
 * <p>登录方式的收敛点：
 * <ul>
 *   <li>邮箱验证码首登 ⇒ 自动建档（username 随机生成，昵称取邮箱前缀）；</li>
 *   <li>已注册账号（账密注册 / 后台建的号）⇒ 邮箱登录直接命中同一账号；</li>
 *   <li>账密注册时若带了邮箱，则该邮箱立即成为可用登录名（一条账号两条路径）。</li>
 * </ul>
 *
 * <p>设备接线（本会话只做最小集）：登录成功时若上报了 {@code deviceUuid}，则按
 * {@code (user_id, device_uuid)} upsert 一行 {@code bl_device} 并回写 last_active_at；
 * 未上报则不落行、不阻断登录。设备注册/心跳/踢出/workerId 分配留 V2.0 后续。
 */
@Service
public class AppAuthServiceImpl implements AppAuthService {

    private static final Logger log = LoggerFactory.getLogger(AppAuthServiceImpl.class);

    /** 自动建档的用户名前缀：与后台建的号、微信小程序号（wx_）在视觉上可区分 */
    private static final String AUTO_USERNAME_PREFIX = "bl_";
    private static final int USERNAME_MAX_ATTEMPTS = 5;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int NICKNAME_MAX_LENGTH = 32;
    private static final int DEVICE_NAME_MAX_LENGTH = 100;
    private static final int APP_VERSION_MAX_LENGTH = 32;
    private static final String DEFAULT_PLATFORM = "UNKNOWN";
    private static final String DEVICE_ACTIVE = "ACTIVE";

    private final EmailCodeService emailCodeService;
    private final AppTokenService tokenService;
    private final RefreshTokenStore refreshTokenStore;
    /** 只读契约：按 ID / 用户名取账号（refresh 校验账号状态、账密登录按用户名命中） */
    private final IUserFacade userFacade;
    /** 写契约：按邮箱查、查重、建号、校验密码（M5 收口后取代原 SysUserAccountDao） */
    private final IUserAccountFacade userAccountFacade;
    private final BlDeviceMapper deviceMapper;
    private final BabyLogAuthProperties properties;

    public AppAuthServiceImpl(EmailCodeService emailCodeService,
                              AppTokenService tokenService,
                              RefreshTokenStore refreshTokenStore,
                              IUserFacade userFacade,
                              IUserAccountFacade userAccountFacade,
                              BlDeviceMapper deviceMapper,
                              BabyLogAuthProperties properties) {
        this.emailCodeService = emailCodeService;
        this.tokenService = tokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.userFacade = userFacade;
        this.userAccountFacade = userAccountFacade;
        this.deviceMapper = deviceMapper;
        this.properties = properties;
    }

    @Override
    public CodeSendVO sendLoginCode(EmailCodeCmd cmd) {
        return emailCodeService.sendLoginCode(cmd.getEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginTokenVO loginByEmailCode(EmailLoginCmd cmd) {
        String email = emailCodeService.normalize(cmd.getEmail());
        // 先校验验证码：没有有效验证码的人，连「这个邮箱有没有账号」都不该被试探出来
        emailCodeService.verify(email, cmd.getCode());

        UserDTO existing = userAccountFacade.getByEmail(email);
        if (existing != null) {
            requireEnabled(existing);
            return completeLogin(existing, false, cmd.getDevice(), "");
        }
        UserDTO created = createAccountForEmail(email);
        log.info("[BABYLOG][auth] 邮箱首登自动建档 userId={} email={}", created.getId(), maskEmail(email));
        return completeLogin(created, true, cmd.getDevice(), "");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginTokenVO register(RegisterCmd cmd) {
        String username = cmd.getUsername().trim();
        if (userAccountFacade.existsByUsername(username)) {
            throw new ServiceException(BabyLogErrorCode.USERNAME_ALREADY_USED);
        }
        String email = "";
        if (StringUtils.hasText(cmd.getEmail())) {
            email = emailCodeService.normalize(cmd.getEmail());
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new ServiceException(BabyLogErrorCode.EMAIL_FORMAT_INVALID);
            }
            if (userAccountFacade.existsByEmail(email)) {
                throw new ServiceException(BabyLogErrorCode.EMAIL_ALREADY_USED);
            }
        }
        String nickname = StringUtils.hasText(cmd.getNickname())
                ? truncate(cmd.getNickname().trim(), NICKNAME_MAX_LENGTH)
                : username;
        Long userId = createAccount(username, nickname, email, cmd.getPassword());
        UserDTO created = requireUser(userId);
        log.info("[BABYLOG][auth] 账密注册成功 userId={} username={}", userId, username);
        return completeLogin(created, true, cmd.getDevice(), "");
    }

    @Override
    public LoginTokenVO loginByPassword(PasswordLoginCmd cmd) {
        String account = cmd.getAccount().trim();
        UserDTO found = account.contains("@")
                ? userAccountFacade.getByEmail(account.toLowerCase(java.util.Locale.ROOT))
                : userFacade.getByUsername(account);
        // 与 /app/auth/login 同纪律：账号不存在与密码错误统一 2001，不泄露账号是否存在
        if (found == null || !userAccountFacade.checkPassword(found.getId(), cmd.getPassword())) {
            throw new ServiceException(SystemErrorCode.LOGIN_FAILED);
        }
        requireEnabled(found);
        return completeLogin(found, false, cmd.getDevice(), "");
    }

    @Override
    public LoginTokenVO refresh(RefreshTokenCmd cmd) {
        RefreshTokenRecord record = refreshTokenStore.resolve(cmd.getRefreshToken())
                .orElseThrow(() -> new ServiceException(BabyLogErrorCode.REFRESH_TOKEN_INVALID));
        UserDTO user = userFacade.getById(record.userId());
        if (user == null) {
            throw new ServiceException(BabyLogErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!enabled(user)) {
            // 账号被停用：refresh 一并作废，避免「停用后靠旧 refresh 无限续命」
            refreshTokenStore.revoke(cmd.getRefreshToken());
            throw new ServiceException(SystemErrorCode.USER_DISABLED);
        }
        String accessToken = tokenService.issueAccessToken(user.getId(), user.getUsername());
        Long deviceId = touchDevice(user.getId(), deviceFromRecord(record.deviceUuid()));

        LoginTokenVO vo = baseVo(user, false, deviceId);
        vo.setAccessToken(accessToken);
        vo.setAccessExpiresIn(tokenService.accessTtlSeconds());
        // refresh 不轮换：原样返回，并把剩余有效期透出（客户端据此判断何时该重新登录）
        vo.setRefreshToken(cmd.getRefreshToken());
        vo.setRefreshExpiresIn(record.remainingSeconds());
        return vo;
    }

    @Override
    public void logout(String refreshToken) {
        tokenService.logoutCurrent();
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenStore.revoke(refreshToken);
        }
    }

    // ------------------------------------------------------------------
    // 内部：登录收口（发双 token + 落设备）
    // ------------------------------------------------------------------

    /**
     * 三种登录方式的公共出口：access（Sa-Token）+ refresh（Redis 哈希）+ 设备行。
     *
     * @param deviceUuidHint 来自 refresh 记录的设备标识（登录场景传空，设备信息在 device 里）
     */
    private LoginTokenVO completeLogin(UserDTO user, boolean newUser,
                                       DeviceInfo device, String deviceUuidHint) {
        String accessToken = tokenService.issueAccessToken(user.getId(), user.getUsername());
        String deviceUuid = device != null && StringUtils.hasText(device.getDeviceUuid())
                ? device.getDeviceUuid() : deviceUuidHint;
        Long deviceId = touchDevice(user.getId(), device);
        String refreshToken = refreshTokenStore.issue(user.getId(), deviceUuid,
                Duration.ofSeconds(properties.getRefreshTokenTtlSeconds()));

        LoginTokenVO vo = baseVo(user, newUser, deviceId);
        vo.setAccessToken(accessToken);
        vo.setAccessExpiresIn(tokenService.accessTtlSeconds());
        vo.setRefreshToken(refreshToken);
        vo.setRefreshExpiresIn(properties.getRefreshTokenTtlSeconds());
        log.info("[BABYLOG][auth] 登录成功 userId={} newUser={} deviceId={}", user.getId(), newUser, deviceId);
        return vo;
    }

    private LoginTokenVO baseVo(UserDTO user, boolean newUser, Long deviceId) {
        LoginTokenVO vo = new LoginTokenVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setNewUser(newUser);
        vo.setDeviceId(deviceId);
        return vo;
    }

    /**
     * 邮箱验证码首登自动建档。
     *
     * <p>username 随机生成而非直接用邮箱：邮箱最多 128 字符而 username 只有 64，
     * 且不应把邮箱当登录名到处可见（账号名会出现在管理端用户列表与导出里）。
     * 邮箱落在 email 列，登录时按 email 查——两条路径指向同一个账号。
     */
    private UserDTO createAccountForEmail(String email) {
        String username = generateUsername();
        String nickname = truncate(email.substring(0, Math.max(0, email.indexOf('@'))), NICKNAME_MAX_LENGTH);
        if (!StringUtils.hasText(nickname)) {
            nickname = "BabyLog 用户";
        }
        // rawPassword 留空：由契约实现侧生成随机密码落库，账号只能用邮箱验证码登录，
        // 账密登录天然进不来（不用 biz 自己造随机串，避免两处口径)
        Long userId = createAccount(username, nickname, email, null);
        return requireUser(userId);
    }

    /**
     * 建号并做错误码转译。
     *
     * <p>契约抛出的是 system 段的 {@code USERNAME_EXISTS(2003)} / {@code EMAIL_ALREADY_USED(2007)}；
     * App 端只认 biz 的 8xxx 段（上游码值将来调整不影响客户端），故在此收口翻译。
     */
    private Long createAccount(String username, String nickname, String email, String rawPassword) {
        UserAccountCreateCmd create = new UserAccountCreateCmd();
        create.setUsername(username);
        create.setNickname(nickname);
        create.setEmail(email);
        create.setRawPassword(rawPassword);
        try {
            return userAccountFacade.createAccount(create);
        } catch (ServiceException e) {
            if (e.getCode() == SystemErrorCode.USERNAME_EXISTS.getCode()) {
                throw new ServiceException(BabyLogErrorCode.USERNAME_ALREADY_USED);
            }
            if (e.getCode() == SystemErrorCode.EMAIL_ALREADY_USED.getCode()) {
                throw new ServiceException(BabyLogErrorCode.EMAIL_ALREADY_USED);
            }
            throw e;
        }
    }

    /** 建号后回读：UserDTO 由 system 侧组装，比在 biz 里手工拼一个「半真半假」的 DTO 可靠 */
    private UserDTO requireUser(Long userId) {
        UserDTO user = userFacade.getById(userId);
        if (user == null) {
            // 建号成功却读不到 = 上游实现异常；宁可 500 也不发一个 userId 无主的 token
            throw new ServiceException("账号创建后读取失败，请重试");
        }
        return user;
    }

    private String generateUsername() {
        for (int i = 0; i < USERNAME_MAX_ATTEMPTS; i++) {
            String candidate = AUTO_USERNAME_PREFIX + RandomUtil.randomString(10);
            if (!userAccountFacade.existsByUsername(candidate)) {
                return candidate;
            }
        }
        // 10 位随机串连续 5 次撞库，实际不可能；真撞上说明随机源坏了，宁可报错也不建重名号
        throw new ServiceException("用户名生成失败，请重试");
    }

    /**
     * 设备行 upsert（本会话最小接线）。
     *
     * <p>没上报 deviceUuid 就整体跳过：没有设备标识的「设备行」没有业务含义，
     * 每次登录都插一行只会把表灌成日志。
     */
    private Long touchDevice(Long userId, DeviceInfo device) {
        if (device == null || !StringUtils.hasText(device.getDeviceUuid())) {
            return null;
        }
        String deviceUuid = truncate(device.getDeviceUuid().trim(), 64);
        List<BlDevice> existing = deviceMapper.selectList(Wrappers.<BlDevice>lambdaQuery()
                .eq(BlDevice::getUserId, userId)
                .eq(BlDevice::getDeviceUuid, deviceUuid));
        LocalDateTime now = LocalDateTime.now();
        if (existing.isEmpty()) {
            BlDevice row = new BlDevice();
            row.setUserId(userId);
            row.setDeviceUuid(deviceUuid);
            row.setDeviceName(truncate(nullToEmpty(device.getDeviceName()), DEVICE_NAME_MAX_LENGTH));
            row.setPlatform(platformOf(device.getPlatform()));
            row.setAppVersion(truncate(nullToEmpty(device.getAppVersion()), APP_VERSION_MAX_LENGTH));
            row.setStatus(DEVICE_ACTIVE);
            row.setLastActiveAt(now);
            deviceMapper.insert(row);
            return row.getId();
        }
        BlDevice row = existing.get(0);
        row.setStatus(DEVICE_ACTIVE);
        row.setLastActiveAt(now);
        if (StringUtils.hasText(device.getDeviceName())) {
            row.setDeviceName(truncate(device.getDeviceName().trim(), DEVICE_NAME_MAX_LENGTH));
        }
        if (StringUtils.hasText(device.getAppVersion())) {
            row.setAppVersion(truncate(device.getAppVersion().trim(), APP_VERSION_MAX_LENGTH));
        }
        if (StringUtils.hasText(device.getPlatform())) {
            row.setPlatform(platformOf(device.getPlatform()));
        }
        deviceMapper.updateById(row);
        return row.getId();
    }

    /** refresh 场景没有 DeviceInfo，只带得回设备标识，够定位设备行 */
    private static DeviceInfo deviceFromRecord(String deviceUuid) {
        if (!StringUtils.hasText(deviceUuid)) {
            return null;
        }
        DeviceInfo device = new DeviceInfo();
        device.setDeviceUuid(deviceUuid);
        return device;
    }

    private static void requireEnabled(UserDTO user) {
        if (!enabled(user)) {
            throw new ServiceException(SystemErrorCode.USER_DISABLED);
        }
    }

    /** 停用账号一律拒绝登录，与 /app/auth/login 的 2002 对齐；status 缺失（脏数据）按启用处理，不误锁人 */
    private static boolean enabled(UserDTO user) {
        Integer status = user.getStatus();
        return status == null || status == 0;
    }

    private static String platformOf(String platform) {
        return StringUtils.hasText(platform) ? platform.trim().toUpperCase(java.util.Locale.ROOT) : DEFAULT_PLATFORM;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
