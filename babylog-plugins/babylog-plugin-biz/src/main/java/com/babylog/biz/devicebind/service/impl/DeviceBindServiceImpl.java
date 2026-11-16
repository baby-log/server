package com.babylog.biz.devicebind.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.dto.DeviceInfo;
import com.babylog.biz.auth.service.AppAuthService;
import com.babylog.biz.auth.vo.LoginTokenVO;
import com.babylog.biz.devicebind.config.BabyLogDeviceBindProperties;
import com.babylog.biz.devicebind.dto.DeviceBindApplyCmd;
import com.babylog.biz.devicebind.dto.DeviceBindPollCmd;
import com.babylog.biz.devicebind.dto.DeviceBindUserCodeCmd;
import com.babylog.biz.devicebind.service.DeviceBindService;
import com.babylog.biz.devicebind.store.DeviceBindClaim;
import com.babylog.biz.devicebind.store.DeviceBindRecord;
import com.babylog.biz.devicebind.store.DeviceBindStore;
import com.babylog.biz.devicebind.support.DeviceBindHash;
import com.babylog.biz.devicebind.vo.DeviceBindApplyVO;
import com.babylog.biz.devicebind.vo.DeviceBindPollVO;
import com.babylog.biz.devicebind.vo.DeviceBindStatusVO;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceBindServiceImpl implements DeviceBindService {

    private static final Logger log = LoggerFactory.getLogger(DeviceBindServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CREATE_ATTEMPTS = 5;

    private final DeviceBindStore store;
    private final AppAuthService authService;
    private final BabyLogDeviceBindProperties properties;

    public DeviceBindServiceImpl(DeviceBindStore store, AppAuthService authService,
                                 BabyLogDeviceBindProperties properties) {
        this.store = store;
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public DeviceBindApplyVO apply(DeviceBindApplyCmd cmd, String clientIp) {
        String ipHash = DeviceBindHash.sha256(normalizeIp(clientIp));
        long count = store.increaseApplyCount(ipHash, Duration.ofMinutes(1));
        if (count > properties.getApplyPerIpPerMinute()) {
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_RATE_LIMITED);
        }
        DeviceInfo device = normalizeDevice(cmd.getDevice());
        Duration ttl = Duration.ofSeconds(properties.getCodeTtlSeconds());
        for (int attempt = 0; attempt < CREATE_ATTEMPTS; attempt++) {
            String userCode = randomUserCode();
            String deviceCode = randomDeviceCode();
            String userCodeHash = DeviceBindHash.sha256(userCode);
            String deviceCodeHash = DeviceBindHash.sha256(deviceCode);
            if (!store.create(userCodeHash, deviceCodeHash, DeviceBindRecord.pending(device), ttl)) {
                continue;
            }
            DeviceBindApplyVO vo = new DeviceBindApplyVO();
            vo.setUserCode(userCode);
            vo.setDeviceCode(deviceCode);
            vo.setExpiresIn(Math.toIntExact(properties.getCodeTtlSeconds()));
            vo.setPollIntervalSeconds(properties.getPollIntervalSeconds());
            log.info("[BABYLOG][device-bind] 申请已创建 deviceHash={} status=PENDING",
                    shortHash(deviceCodeHash));
            return vo;
        }
        throw new ServiceException("设备码生成冲突，请重试");
    }

    @Override
    public DeviceBindPollVO poll(DeviceBindPollCmd cmd) {
        String deviceCode = cmd.getDeviceCode().trim();
        String deviceCodeHash = DeviceBindHash.sha256(deviceCode);
        String claimToken = UUID.randomUUID().toString();
        DeviceBindClaim claim = store.beginClaim(deviceCodeHash, claimToken,
                Duration.ofSeconds(properties.getClaimTtlSeconds()));
        return switch (claim.outcome()) {
            case EXPIRED -> status("expired", null);
            case DENIED -> status("denied", null);
            case CONSUMED -> throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
            case BUSY -> status("pending", null);
            case PENDING -> {
                requirePollSlot(deviceCodeHash);
                yield status("pending", null);
            }
            case APPROVED -> claimLogin(deviceCodeHash, claimToken, claim.record());
        };
    }

    private DeviceBindPollVO claimLogin(String deviceCodeHash, String claimToken,
                                        DeviceBindRecord record) {
        if (!store.acquirePollSlot(deviceCodeHash, Duration.ofSeconds(properties.getPollIntervalSeconds()))) {
            releaseQuietly(deviceCodeHash, claimToken);
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_RATE_LIMITED);
        }
        if (record == null || record.approvedBy() == null) {
            releaseQuietly(deviceCodeHash, claimToken);
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
        }
        final long userId;
        try {
            userId = Long.parseLong(record.approvedBy());
        } catch (NumberFormatException e) {
            releaseQuietly(deviceCodeHash, claimToken);
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
        }

        final LoginTokenVO login;
        try {
            login = authService.loginByBoundDevice(userId, record.device());
        } catch (RuntimeException e) {
            // 没发出完整凭证就释放锁，APPROVED 记录保留，客户端下次轮询可重试。
            releaseQuietly(deviceCodeHash, claimToken);
            throw e;
        }

        final boolean consumed;
        try {
            consumed = store.finishClaim(deviceCodeHash, claimToken, record.approvedBy());
        } catch (RuntimeException e) {
            revokeIssuedLogin(login, deviceCodeHash);
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
        }
        if (!consumed) {
            revokeIssuedLogin(login, deviceCodeHash);
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
        }
        log.info("[BABYLOG][device-bind] 领奖完成 deviceHash={} userId={} deviceId={} status=CONSUMED",
                shortHash(deviceCodeHash), userId, login.getDeviceId());
        return status("approved", login);
    }

    @Override
    public DeviceBindStatusVO confirm(long userId, DeviceBindUserCodeCmd cmd, String clientIp) {
        return transitionByUser(userId, cmd, clientIp, true);
    }

    @Override
    public DeviceBindStatusVO deny(long userId, DeviceBindUserCodeCmd cmd, String clientIp) {
        return transitionByUser(userId, cmd, clientIp, false);
    }

    private DeviceBindStatusVO transitionByUser(long userId, DeviceBindUserCodeCmd cmd,
                                                 String clientIp, boolean approve) {
        String ipHash = DeviceBindHash.sha256(normalizeIp(clientIp));
        requireConfirmUnlocked(userId, ipHash);
        String userCodeHash = DeviceBindHash.sha256(cmd.getUserCode().trim());
        Optional<String> deviceCodeHash = store.resolveDeviceHash(userCodeHash);
        boolean changed = deviceCodeHash.isPresent() && (approve
                ? store.approve(userCodeHash, deviceCodeHash.get(), userId)
                : store.deny(userCodeHash, deviceCodeHash.get()));
        if (!changed) {
            store.increaseConfirmFailures(userId, ipHash,
                    Duration.ofSeconds(properties.getConfirmFailWindowSeconds()));
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_CODE_INVALID);
        }
        store.clearConfirmFailures(userId, ipHash);
        String status = approve ? "approved" : "denied";
        log.info("[BABYLOG][device-bind] 手机处理完成 userId={} deviceHash={} status={}",
                userId, shortHash(deviceCodeHash.orElse("unknown")), status.toUpperCase(java.util.Locale.ROOT));
        return new DeviceBindStatusVO(status);
    }

    private void requireConfirmUnlocked(long userId, String ipHash) {
        if (store.confirmUserFailures(userId) >= properties.getConfirmFailUserLimit()
                || store.confirmIpFailures(ipHash) >= properties.getConfirmFailIpLimit()) {
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_CONFIRM_LOCKED);
        }
    }

    private void requirePollSlot(String deviceCodeHash) {
        if (!store.acquirePollSlot(deviceCodeHash,
                Duration.ofSeconds(properties.getPollIntervalSeconds()))) {
            throw new ServiceException(BabyLogErrorCode.DEVICE_BIND_RATE_LIMITED);
        }
    }

    private void releaseQuietly(String deviceCodeHash, String claimToken) {
        try {
            store.releaseClaim(deviceCodeHash, claimToken);
        } catch (RuntimeException releaseError) {
            log.warn("[BABYLOG][device-bind] 释放领奖锁失败 deviceHash={}", shortHash(deviceCodeHash));
        }
    }

    private void revokeIssuedLogin(LoginTokenVO login, String deviceCodeHash) {
        try {
            // 匿名 poll 请求刚由同一出口完成 login，logoutCurrent 注销的正是这张 access。
            authService.logout(login.getRefreshToken());
        } catch (RuntimeException logoutError) {
            log.error("[BABYLOG][device-bind] 消费失败后撤销凭证失败 deviceHash={}", shortHash(deviceCodeHash));
        }
        log.warn("[BABYLOG][device-bind] 领奖未获最终消费权，凭证已撤销 deviceHash={}",
                shortHash(deviceCodeHash));
    }

    private DeviceBindPollVO status(String status, LoginTokenVO login) {
        DeviceBindPollVO vo = new DeviceBindPollVO();
        vo.setStatus(status);
        vo.setRetryAfterSeconds("pending".equals(status) ? properties.getPollIntervalSeconds() : 0);
        vo.setLogin(login);
        return vo;
    }

    private static DeviceInfo normalizeDevice(DeviceInfo source) {
        if (source == null || !StringUtils.hasText(source.getDeviceUuid())) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID, "deviceUuid 不能为空");
        }
        DeviceInfo device = new DeviceInfo();
        device.setDeviceUuid(limitedTrim(source.getDeviceUuid(), 64, "deviceUuid"));
        device.setDeviceName(limitedTrim(source.getDeviceName(), 100, "deviceName"));
        String platform = StringUtils.hasText(source.getPlatform()) ? source.getPlatform() : "ANDROID_TV";
        device.setPlatform(limitedTrim(platform, 32, "platform").toUpperCase(java.util.Locale.ROOT));
        device.setAppVersion(limitedTrim(source.getAppVersion(), 32, "appVersion"));
        return device;
    }

    private static String limitedTrim(String value, int max, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new ServiceException(GlobalErrorCode.PARAM_INVALID,
                    field + " 长度不能超过 " + max);
        }
        return trimmed;
    }

    private static String normalizeIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";
    }

    private static String randomUserCode() {
        return String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private static String randomDeviceCode() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String shortHash(String hash) {
        return hash.length() <= 12 ? hash : hash.substring(0, 12);
    }
}
