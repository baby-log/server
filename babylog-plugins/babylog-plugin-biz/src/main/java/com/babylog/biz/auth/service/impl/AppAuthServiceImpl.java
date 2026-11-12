package com.babylog.biz.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
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
import com.babylog.biz.auth.support.SysUserAccountDao;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.biz.auth.vo.LoginTokenVO;
import com.babylog.biz.entity.BlDevice;
import com.babylog.biz.mapper.BlDeviceMapper;
import com.babylog.common.core.exception.ServiceException;
import com.babylog.system.api.enums.SystemErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final SysUserAccountDao userAccountDao;
    private final BlDeviceMapper deviceMapper;
    private final BabyLogAuthProperties properties;

    public AppAuthServiceImpl(EmailCodeService emailCodeService,
                              AppTokenService tokenService,
                              RefreshTokenStore refreshTokenStore,
                              SysUserAccountDao userAccountDao,
                              BlDeviceMapper deviceMapper,
                              BabyLogAuthProperties properties) {
        this.emailCodeService = emailCodeService;
        this.tokenService = tokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.userAccountDao = userAccountDao;
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

        Optional<SysUserAccountDao.SysUserRow> existing = userAccountDao.findByEmail(email);
        if (existing.isPresent()) {
            SysUserAccountDao.SysUserRow user = existing.get();
            requireEnabled(user);
            return completeLogin(user, false, cmd.getDevice(), "");
        }
        SysUserAccountDao.SysUserRow created = createAccountForEmail(email);
        log.info("[BABYLOG][auth] 邮箱首登自动建档 userId={} email={}", created.id(), maskEmail(email));
        return completeLogin(created, true, cmd.getDevice(), "");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginTokenVO register(RegisterCmd cmd) {
        String username = cmd.getUsername().trim();
        if (userAccountDao.findByUsername(username).isPresent()) {
            throw new ServiceException(SystemErrorCode.USERNAME_EXISTS);
        }
        String email = "";
        if (StringUtils.hasText(cmd.getEmail())) {
            email = emailCodeService.normalize(cmd.getEmail());
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new ServiceException(BabyLogErrorCode.EMAIL_FORMAT_INVALID);
            }
            if (userAccountDao.findByEmail(email).isPresent()) {
                throw new ServiceException(BabyLogErrorCode.EMAIL_ALREADY_USED);
            }
        }
        String nickname = StringUtils.hasText(cmd.getNickname())
                ? truncate(cmd.getNickname().trim(), NICKNAME_MAX_LENGTH)
                : username;
        long userId = userAccountDao.insert(username, nickname, email, BCrypt.hashpw(cmd.getPassword()));
        SysUserAccountDao.SysUserRow created =
                new SysUserAccountDao.SysUserRow(userId, username, nickname, "", email, 0);
        log.info("[BABYLOG][auth] 账密注册成功 userId={} username={}", userId, username);
        return completeLogin(created, true, cmd.getDevice(), "");
    }

    @Override
    public LoginTokenVO loginByPassword(PasswordLoginCmd cmd) {
        String account = cmd.getAccount().trim();
        Optional<SysUserAccountDao.SysUserRow> found = account.contains("@")
                ? userAccountDao.findByEmail(account.toLowerCase(java.util.Locale.ROOT))
                : userAccountDao.findByUsername(account);
        // 与 /app/auth/login 同纪律：账号不存在与密码错误统一 2001，不泄露账号是否存在
        SysUserAccountDao.SysUserRow user = found.filter(u -> passwordMatches(cmd.getPassword(), u.password()))
                .orElseThrow(() -> new ServiceException(SystemErrorCode.LOGIN_FAILED));
        requireEnabled(user);
        return completeLogin(user, false, cmd.getDevice(), "");
    }

    @Override
    public LoginTokenVO refresh(RefreshTokenCmd cmd) {
        RefreshTokenRecord record = refreshTokenStore.resolve(cmd.getRefreshToken())
                .orElseThrow(() -> new ServiceException(BabyLogErrorCode.REFRESH_TOKEN_INVALID));
        SysUserAccountDao.SysUserRow user = userAccountDao.findById(record.userId())
                .orElseThrow(() -> new ServiceException(BabyLogErrorCode.REFRESH_TOKEN_INVALID));
        if (!user.enabled()) {
            // 账号被停用：refresh 一并作废，避免「停用后靠旧 refresh 无限续命」
            refreshTokenStore.revoke(cmd.getRefreshToken());
            throw new ServiceException(SystemErrorCode.USER_DISABLED);
        }
        String accessToken = tokenService.issueAccessToken(user.id(), user.username());
        Long deviceId = touchDevice(user.id(), deviceFromRecord(record.deviceUuid()));

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
    private LoginTokenVO completeLogin(SysUserAccountDao.SysUserRow user, boolean newUser,
                                       DeviceInfo device, String deviceUuidHint) {
        String accessToken = tokenService.issueAccessToken(user.id(), user.username());
        String deviceUuid = device != null && StringUtils.hasText(device.getDeviceUuid())
                ? device.getDeviceUuid() : deviceUuidHint;
        Long deviceId = touchDevice(user.id(), device);
        String refreshToken = refreshTokenStore.issue(user.id(), deviceUuid,
                Duration.ofSeconds(properties.getRefreshTokenTtlSeconds()));

        LoginTokenVO vo = baseVo(user, newUser, deviceId);
        vo.setAccessToken(accessToken);
        vo.setAccessExpiresIn(tokenService.accessTtlSeconds());
        vo.setRefreshToken(refreshToken);
        vo.setRefreshExpiresIn(properties.getRefreshTokenTtlSeconds());
        log.info("[BABYLOG][auth] 登录成功 userId={} newUser={} deviceId={}", user.id(), newUser, deviceId);
        return vo;
    }

    private LoginTokenVO baseVo(SysUserAccountDao.SysUserRow user, boolean newUser, Long deviceId) {
        LoginTokenVO vo = new LoginTokenVO();
        vo.setUserId(user.id());
        vo.setUsername(user.username());
        vo.setNickname(user.nickname());
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
    private SysUserAccountDao.SysUserRow createAccountForEmail(String email) {
        String username = generateUsername();
        String nickname = truncate(email.substring(0, Math.max(0, email.indexOf('@'))), NICKNAME_MAX_LENGTH);
        if (!StringUtils.hasText(nickname)) {
            nickname = "BabyLog 用户";
        }
        // 自动建档的账号没有可用密码（随机 32 位），只能用邮箱验证码登录——账密登录天然进不来
        long userId = userAccountDao.insert(username, nickname, email, BCrypt.hashpw(RandomUtil.randomString(32)));
        return new SysUserAccountDao.SysUserRow(userId, username, nickname, "", email, 0);
    }

    private String generateUsername() {
        for (int i = 0; i < USERNAME_MAX_ATTEMPTS; i++) {
            String candidate = AUTO_USERNAME_PREFIX + RandomUtil.randomString(10);
            if (userAccountDao.findByUsername(candidate).isEmpty()) {
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

    private static void requireEnabled(SysUserAccountDao.SysUserRow user) {
        if (!user.enabled()) {
            throw new ServiceException(SystemErrorCode.USER_DISABLED);
        }
    }

    /** BCrypt 校验；账号可能是微信建档（有密码串）或异常数据（空密码），空密码一律视为不匹配 */
    private static boolean passwordMatches(String rawPassword, String hashed) {
        if (!StringUtils.hasText(hashed) || !StringUtils.hasText(rawPassword)) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, hashed);
        } catch (IllegalArgumentException e) {
            // 哈希串格式非法（历史脏数据）：不抛 500，按密码错误处理
            return false;
        }
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
