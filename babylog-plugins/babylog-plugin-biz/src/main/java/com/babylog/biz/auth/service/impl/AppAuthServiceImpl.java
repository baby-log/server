package com.babylog.biz.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.biz.auth.dto.DeviceInfo;
import com.babylog.biz.auth.dto.EmailCodeCmd;
import com.babylog.biz.auth.dto.EmailLoginCmd;
import com.babylog.biz.auth.dto.MiniLoginCmd;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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
 * <p>设备接线：登录成功时若上报了 {@code deviceUuid}，则按
 * {@code (user_id, device_uuid)} upsert 一行 {@code bl_device} 并回写 last_active_at，
 * 同时分配设备级雪花 {@code workerId}（同用户内 max+1，上限 1023）随登录响应回传；
 * 未上报则不落行、不阻断登录（但 App 拿不到 workerId，也就发不了雪花 ID，属于客户端 bug）。
 * 设备注册/心跳/踢出端点留 V2.0 后续。
 */
@Service
public class AppAuthServiceImpl implements AppAuthService {

    private static final Logger log = LoggerFactory.getLogger(AppAuthServiceImpl.class);

    /** 自动建档的用户名前缀：与后台建的号、微信小程序号（wx_）在视觉上可区分 */
    private static final String AUTO_USERNAME_PREFIX = "bl_";
    /** 小程序自动建档用户名前缀：与 system 手机号建档（MiniAuthServiceImpl）的 wx_ 前缀同口径 */
    private static final String WX_USERNAME_PREFIX = "wx_";
    /** 小程序登录的微信应用标识：对应 yml `babylog.wechat.miniapp.configs.babylog` 配置位 */
    private static final String WX_MINI_APP = "babylog";
    /** 绑定渠道（sys_social_user.channel，与 system MiniAuthService.CHANNEL_WECHAT_MINI 同值） */
    private static final String CHANNEL_WECHAT_MINI = "wechat-mini";
    private static final int USERNAME_MAX_ATTEMPTS = 5;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int NICKNAME_MAX_LENGTH = 32;
    private static final int DEVICE_NAME_MAX_LENGTH = 100;
    private static final int APP_VERSION_MAX_LENGTH = 32;
    private static final String DEFAULT_PLATFORM = "UNKNOWN";
    /** 设备状态常量统一取自 {@link BlDevice}（设备管理端点 M8 也用同一份） */
    private static final String DEVICE_ACTIVE = BlDevice.STATUS_ACTIVE;

    private final EmailCodeService emailCodeService;
    private final AppTokenService tokenService;
    private final RefreshTokenStore refreshTokenStore;
    /** 只读契约：按 ID / 用户名取账号（refresh 校验账号状态、账密登录按用户名命中） */
    private final IUserFacade userFacade;
    /** 写契约：按邮箱查、查重、建号、校验密码（M5 收口后取代原 SysUserAccountDao） */
    private final IUserAccountFacade userAccountFacade;
    /** 微信小程序 API 契约（M20）：code 换 session；appid/secret 的解析与 access_token 缓存留在 system 侧 */
    private final IWechatMiniFacade wechatMiniFacade;
    /** 社交绑定契约（M20）：(channel, openId) → sys_user 的绑定查询与写入 */
    private final ISocialAccountFacade socialAccountFacade;
    private final BlDeviceMapper deviceMapper;
    private final DeviceWorkerIdAllocator workerIdAllocator;
    private final BabyLogAuthProperties properties;

    public AppAuthServiceImpl(EmailCodeService emailCodeService,
                              AppTokenService tokenService,
                              RefreshTokenStore refreshTokenStore,
                              IUserFacade userFacade,
                              IUserAccountFacade userAccountFacade,
                              IWechatMiniFacade wechatMiniFacade,
                              ISocialAccountFacade socialAccountFacade,
                              BlDeviceMapper deviceMapper,
                              DeviceWorkerIdAllocator workerIdAllocator,
                              BabyLogAuthProperties properties) {
        this.emailCodeService = emailCodeService;
        this.tokenService = tokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.userFacade = userFacade;
        this.userAccountFacade = userAccountFacade;
        this.wechatMiniFacade = wechatMiniFacade;
        this.socialAccountFacade = socialAccountFacade;
        this.deviceMapper = deviceMapper;
        this.workerIdAllocator = workerIdAllocator;
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
    @Transactional(rollbackFor = Exception.class)
    public LoginTokenVO loginByWxMini(MiniLoginCmd cmd) {
        WechatSessionDTO session = code2Session(cmd.getCode());
        SocialBindingDTO binding =
                socialAccountFacade.findByChannelOpenId(CHANNEL_WECHAT_MINI, session.getOpenId());

        UserDTO user;
        boolean newUser;
        if (binding == null) {
            user = createAccountForWxMini();
            newUser = true;
            try {
                socialAccountFacade.bind(user.getId(), CHANNEL_WECHAT_MINI,
                        session.getOpenId(), session.getUnionId());
            } catch (ServiceException e) {
                if (e.getCode() == SystemErrorCode.SOCIAL_ALREADY_BOUND.getCode()) {
                    // find 与 bind 之间的并发窗口：另一个请求先把同一 openId 绑走了。
                    // 客户端重试一次即可按已绑定路径登录，这里报冲突码而非静默换绑。
                    throw new ServiceException(BabyLogErrorCode.WX_MINI_BOUND_CONFLICT);
                }
                throw e;
            }
            log.info("[BABYLOG][auth] 微信小程序首登自动建档 userId={} openId={}",
                    user.getId(), maskOpenId(session.getOpenId()));
        } else {
            user = userFacade.getById(binding.getUserId());
            if (user == null) {
                // 孤儿绑定：指向已删除的账号。换绑属管理动作，登录路不做，留日志供排查
                log.warn("[BABYLOG][auth] 小程序绑定指向不存在账号 userId={} openId={}",
                        binding.getUserId(), maskOpenId(session.getOpenId()));
                throw new ServiceException(BabyLogErrorCode.WX_MINI_BOUND_CONFLICT);
            }
            requireEnabled(user);
            // unionId 后补：微信有时首登不下发，后续登录遇到就补齐（与 system 同款语义）；
            // 值没变就不调契约（facade 实现侧还有一层同判断，双保险）
            if (StringUtils.hasText(session.getUnionId())
                    && !session.getUnionId().equals(binding.getUnionId())) {
                socialAccountFacade.updateUnionId(CHANNEL_WECHAT_MINI,
                        session.getOpenId(), session.getUnionId());
            }
            newUser = false;
        }
        // 与邮箱/账密登录同一出口：app-user 双 token + bl_device 落行 + workerId。
        // 小程序在服务端眼里就是「一台设备」，家庭/同步端点零改动（M20 拍板）。
        return completeLogin(user, newUser, cmd.getDevice(), "");
    }

    /** code 换 session，并把 system 段错误码翻译成 biz 的 8395 段（App/小程序端只认 8xxx） */
    private WechatSessionDTO code2Session(String code) {
        try {
            return wechatMiniFacade.code2Session(WX_MINI_APP, code);
        } catch (ServiceException e) {
            if (e.getCode() == SystemErrorCode.SOCIAL_NOT_CONFIGURED.getCode()) {
                throw new ServiceException(BabyLogErrorCode.WX_MINI_NOT_CONFIGURED);
            }
            if (e.getCode() == SystemErrorCode.SOCIAL_CODE_INVALID.getCode()
                    || e.getCode() == SystemErrorCode.SOCIAL_API_FAILED.getCode()) {
                throw new ServiceException(BabyLogErrorCode.WX_CODE_INVALID);
            }
            throw e;
        }
    }

    /** 微信小程序首登自动建档：wx_ 前缀随机用户名 + 随机密码（仅能靠微信身份登录） */
    private UserDTO createAccountForWxMini() {
        String username = generateUsername(WX_USERNAME_PREFIX);
        Long userId = createAccount(username, "微信用户", "", null);
        return requireUser(userId);
    }

    /**
     * 电视设备码确认后的登录路（M45）：用户身份来自手机确认时写入的绑定记录，
     * 仍走统一 completeLogin，不复制 token、设备行或 workerId 逻辑。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginTokenVO loginByBoundDevice(long userId, DeviceInfo device) {
        UserDTO user = userFacade.getById(userId);
        if (user == null) {
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_CODE_INVALID);
        }
        requireEnabled(user);
        return completeLogin(user, false, device, "");
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
        // 设备被踢（M8 设备管理）：旧 refresh 作废，让客户端清凭证走重新登录。
        // 用 8376 而不是新错误码——App 端 AuthRepository 只对 8376/1002 走「清凭证」分支，
        // 换别的码会被当成可重试的网络错误无限退避。
        // 已知宽限：该设备若之后重新登录，行回到 ACTIVE，仍在 Redis 里未过期的旧 refresh
        // 会随之恢复有效——轮换 + 复用检测等 V2.1 家庭共享一起上（RefreshTokenStore 注释有登记）。
        if (isDeviceKicked(user.getId(), record.deviceUuid())) {
            refreshTokenStore.revoke(cmd.getRefreshToken());
            throw new ServiceException(BabyLogErrorCode.REFRESH_TOKEN_INVALID);
        }
        String accessToken = tokenService.issueAccessToken(user.getId(), user.getUsername());
        DeviceTouch touch = touchDevice(user.getId(), deviceFromRecord(record.deviceUuid()));

        LoginTokenVO vo = baseVo(user, false, touch);
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
     * 三种登录方式的公共出口：access（Sa-Token）+ refresh（Redis 哈希）+ 设备行 + workerId。
     *
     * @param deviceUuidHint 来自 refresh 记录的设备标识（登录场景传空，设备信息在 device 里）
     */
    private LoginTokenVO completeLogin(UserDTO user, boolean newUser,
                                       DeviceInfo device, String deviceUuidHint) {
        String accessToken = tokenService.issueAccessToken(user.getId(), user.getUsername());
        String deviceUuid = device != null && StringUtils.hasText(device.getDeviceUuid())
                ? device.getDeviceUuid() : deviceUuidHint;
        DeviceTouch touch = touchDevice(user.getId(), device);
        String refreshToken = refreshTokenStore.issue(user.getId(), deviceUuid,
                Duration.ofSeconds(properties.getRefreshTokenTtlSeconds()));

        LoginTokenVO vo = baseVo(user, newUser, touch);
        vo.setAccessToken(accessToken);
        vo.setAccessExpiresIn(tokenService.accessTtlSeconds());
        vo.setRefreshToken(refreshToken);
        vo.setRefreshExpiresIn(properties.getRefreshTokenTtlSeconds());
        log.info("[BABYLOG][auth] 登录成功 userId={} newUser={} deviceId={} workerId={}",
                user.getId(), newUser, touch.deviceId(), touch.workerId());
        return vo;
    }

    private LoginTokenVO baseVo(UserDTO user, boolean newUser, DeviceTouch touch) {
        LoginTokenVO vo = new LoginTokenVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setNewUser(newUser);
        vo.setDeviceId(touch.deviceId());
        vo.setWorkerId(touch.workerId());
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
        return generateUsername(AUTO_USERNAME_PREFIX);
    }

    private String generateUsername(String prefix) {
        for (int i = 0; i < USERNAME_MAX_ATTEMPTS; i++) {
            String candidate = prefix + RandomUtil.randomString(10);
            if (!userAccountFacade.existsByUsername(candidate)) {
                return candidate;
            }
        }
        // 10 位随机串连续 5 次撞库，实际不可能；真撞上说明随机源坏了，宁可报错也不建重名号
        throw new ServiceException("用户名生成失败，请重试");
    }

    /** openId 打码：只留前 4 位，日志里够定位、不至于全量暴露用户身份 */
    private static String maskOpenId(String openId) {
        if (openId == null || openId.length() <= 4) {
            return "****";
        }
        return openId.substring(0, 4) + "****";
    }

    /**
     * 设备行 upsert（登录时调用），同时保证 workerId 有值。
     *
     * <p>没上报 deviceUuid 就整体跳过：没有设备标识的「设备行」没有业务含义，
     * 每次登录都插一行只会把表灌成日志；而此时也拿不到 workerId，App 侧应把 deviceUuid
     * 当作登录的必填项。
     *
     * <p>并发登录同一台设备（双击登录、两路请求）会撞 {@code uk(user_id, device_uuid)}：
     * 这不是错误，回读已存在的行按「已注册」继续即可——把一次撞键变成一次成功登录。
     * 若回读仍为空，说明撞的是 {@code uk(user_id, worker_id)}（两台设备抢同一个号），
     * 只能让客户端重试。
     */
    private DeviceTouch touchDevice(Long userId, DeviceInfo device) {
        if (device == null || !StringUtils.hasText(device.getDeviceUuid())) {
            return DeviceTouch.EMPTY;
        }
        String deviceUuid = truncate(device.getDeviceUuid().trim(), 64);
        BlDevice row = findDevice(userId, deviceUuid);
        if (row == null) {
            BlDevice created = new BlDevice();
            created.setUserId(userId);
            created.setDeviceUuid(deviceUuid);
            created.setDeviceName(truncate(nullToEmpty(device.getDeviceName()), DEVICE_NAME_MAX_LENGTH));
            created.setPlatform(platformOf(device.getPlatform()));
            created.setAppVersion(truncate(nullToEmpty(device.getAppVersion()), APP_VERSION_MAX_LENGTH));
            created.setStatus(DEVICE_ACTIVE);
            created.setLastActiveAt(LocalDateTime.now());
            created.setWorkerId(workerIdAllocator.nextWorkerId(userId));
            try {
                deviceMapper.insert(created);
                return new DeviceTouch(created.getId(), created.getWorkerId());
            } catch (DuplicateKeyException e) {
                row = findDevice(userId, deviceUuid);
                if (row == null) {
                    throw new ServiceException("设备注册冲突，请重试");
                }
            }
        }
        row.setStatus(DEVICE_ACTIVE);
        row.setLastActiveAt(LocalDateTime.now());
        if (StringUtils.hasText(device.getDeviceName())) {
            row.setDeviceName(truncate(device.getDeviceName().trim(), DEVICE_NAME_MAX_LENGTH));
        }
        if (StringUtils.hasText(device.getAppVersion())) {
            row.setAppVersion(truncate(device.getAppVersion().trim(), APP_VERSION_MAX_LENGTH));
        }
        if (StringUtils.hasText(device.getPlatform())) {
            row.setPlatform(platformOf(device.getPlatform()));
        }
        if (row.getWorkerId() == null) {
            // V2.2.7 之前落的老行没有 workerId：下次登录补一个，不改已有值（改了会让该设备历史 ID 失去解释）
            row.setWorkerId(workerIdAllocator.nextWorkerId(userId));
        }
        deviceMapper.updateById(row);
        return new DeviceTouch(row.getId(), row.getWorkerId());
    }

    private BlDevice findDevice(Long userId, String deviceUuid) {
        List<BlDevice> rows = deviceMapper.selectList(Wrappers.<BlDevice>lambdaQuery()
                .eq(BlDevice::getUserId, userId)
                .eq(BlDevice::getDeviceUuid, deviceUuid));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** refresh 前置检查：该设备行是否处于 KICKED（查不到行=refresh 记录先于设备行存在，放行） */
    private boolean isDeviceKicked(Long userId, String deviceUuid) {
        if (!StringUtils.hasText(deviceUuid)) {
            return false;
        }
        BlDevice row = findDevice(userId, deviceUuid);
        return row != null && BlDevice.STATUS_KICKED.equals(row.getStatus());
    }

    /** 登录/刷新回传给客户端的设备信息：行 id 用于设备管理，workerId 用于生成雪花 ID */
    private record DeviceTouch(Long deviceId, Integer workerId) {
        static final DeviceTouch EMPTY = new DeviceTouch(null, null);
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
