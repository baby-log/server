package com.babylog.biz.devicebind.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.dto.DeviceInfo;
import com.babylog.biz.auth.service.AppAuthService;
import com.babylog.biz.auth.vo.LoginTokenVO;
import com.babylog.biz.devicebind.config.BabyLogDeviceBindProperties;
import com.babylog.biz.devicebind.dto.DeviceBindApplyCmd;
import com.babylog.biz.devicebind.dto.DeviceBindPollCmd;
import com.babylog.biz.devicebind.dto.DeviceBindUserCodeCmd;
import com.babylog.biz.devicebind.store.DeviceBindClaim;
import com.babylog.biz.devicebind.store.DeviceBindRecord;
import com.babylog.biz.devicebind.store.DeviceBindStore;
import com.babylog.biz.devicebind.support.DeviceBindHash;
import com.babylog.biz.devicebind.vo.DeviceBindApplyVO;
import com.babylog.biz.devicebind.vo.DeviceBindPollVO;
import com.babylog.common.core.enums.error.ErrorCode;
import com.babylog.common.core.enums.error.GlobalErrorCode;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceBindServiceImplTest {

    private static final long USER_ID = 9007199254740993L;
    private static final String DEVICE_HASH = DeviceBindHash.sha256("private-device-code");

    @Mock
    private DeviceBindStore store;
    @Mock
    private AppAuthService authService;

    private BabyLogDeviceBindProperties properties;
    private DeviceBindServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new BabyLogDeviceBindProperties();
        service = new DeviceBindServiceImpl(store, authService, properties);
        when(store.increaseApplyCount(anyString(), any())).thenReturn(1L);
        when(store.create(anyString(), anyString(), any(), any())).thenReturn(true);
        when(store.acquirePollSlot(anyString(), any())).thenReturn(true);
        when(store.finishClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(store.confirmUserFailures(anyLong())).thenReturn(0L);
        when(store.confirmIpFailures(anyString())).thenReturn(0L);
    }

    @Test
    void applyReturnsRawCodesButStoresOnlyHashesAndDevice() {
        DeviceBindApplyVO vo = service.apply(applyCmd(), "192.168.1.8");

        assertThat(vo.getUserCode()).matches("\\d{6}");
        assertThat(vo.getDeviceCode()).hasSize(43).doesNotContain(vo.getUserCode());
        assertThat(vo.getExpiresIn()).isEqualTo(600);
        assertThat(vo.getPollIntervalSeconds()).isEqualTo(5);
        ArgumentCaptor<String> userHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deviceHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DeviceBindRecord> record = ArgumentCaptor.forClass(DeviceBindRecord.class);
        verify(store).create(userHash.capture(), deviceHash.capture(), record.capture(), eq(Duration.ofSeconds(600)));
        assertThat(userHash.getValue()).isEqualTo(DeviceBindHash.sha256(vo.getUserCode()));
        assertThat(deviceHash.getValue()).isEqualTo(DeviceBindHash.sha256(vo.getDeviceCode()));
        assertThat(record.getValue().status()).isEqualTo(DeviceBindRecord.PENDING);
        assertThat(record.getValue().approvedBy()).isNull();
        assertThat(record.getValue().device().getDeviceUuid()).isEqualTo("tv-living-room");
        assertThat(record.getValue().device().getPlatform()).isEqualTo("ANDROID_TV");
        assertThat(record.getValue().toString()).doesNotContain(vo.getUserCode(), vo.getDeviceCode());
    }

    @Test
    void applyRetriesAtomicCodeCollision() {
        when(store.create(anyString(), anyString(), any(), any())).thenReturn(false, true);

        DeviceBindApplyVO vo = service.apply(applyCmd(), "127.0.0.1");

        assertThat(vo.getDeviceCode()).isNotBlank();
        verify(store, times(2)).create(anyString(), anyString(), any(), any());
        verify(store, times(1)).increaseApplyCount(anyString(), any());
    }

    @Test
    void applyRejectsRateLimitBeforeCreatingCodes() {
        when(store.increaseApplyCount(anyString(), any())).thenReturn(6L);

        assertError(() -> service.apply(applyCmd(), "127.0.0.1"),
                BabyLogErrorCode.DEVICE_BIND_RATE_LIMITED);
        verify(store, never()).create(anyString(), anyString(), any(), any());
    }

    @Test
    void applyRejectsBlankDeviceUuid() {
        DeviceBindApplyCmd cmd = applyCmd();
        cmd.getDevice().setDeviceUuid("  ");

        assertError(() -> service.apply(cmd, "127.0.0.1"), GlobalErrorCode.PARAM_INVALID);
        verify(store, never()).create(anyString(), anyString(), any(), any());
    }

    @Test
    void pollPendingDoesNotExposeLogin() {
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.PENDING, null));

        DeviceBindPollVO vo = service.poll(pollCmd());

        assertThat(vo.getStatus()).isEqualTo("pending");
        assertThat(vo.getRetryAfterSeconds()).isEqualTo(5);
        assertThat(vo.getLogin()).isNull();
        verify(authService, never()).loginByBoundDevice(anyLong(), any());
    }

    @Test
    void pollPendingEnforcesInterval() {
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.PENDING, null));
        when(store.acquirePollSlot(anyString(), any())).thenReturn(false);

        assertError(this::poll, BabyLogErrorCode.DEVICE_BIND_RATE_LIMITED);
    }

    @Test
    void pollDeniedAndExpiredAreTerminalWithoutLogin() {
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.DENIED, null));
        assertThat(poll().getStatus()).isEqualTo("denied");
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.EXPIRED, null));
        assertThat(poll().getStatus()).isEqualTo("expired");
        verify(authService, never()).loginByBoundDevice(anyLong(), any());
    }

    @Test
    void consumedReplayReturns8403WithoutAnotherToken() {
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.CONSUMED, null));

        assertError(this::poll, BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
        verify(authService, never()).loginByBoundDevice(anyLong(), any());
    }

    @Test
    void anotherClaimInProgressLooksPending() {
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.BUSY, null));

        DeviceBindPollVO vo = poll();

        assertThat(vo.getStatus()).isEqualTo("pending");
        assertThat(vo.getLogin()).isNull();
    }

    @Test
    void approvedClaimReturnsSameLoginAndConsumesExactStringUserId() {
        DeviceBindRecord record = approvedRecord();
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.APPROVED, record));
        LoginTokenVO login = login();
        when(authService.loginByBoundDevice(USER_ID, record.device())).thenReturn(login);

        DeviceBindPollVO vo = poll();

        assertThat(vo.getStatus()).isEqualTo("approved");
        assertThat(vo.getLogin()).isSameAs(login);
        verify(store).finishClaim(eq(DEVICE_HASH), anyString(), eq(Long.toString(USER_ID)));
        verify(store, never()).releaseClaim(anyString(), anyString());
    }

    @Test
    void approvedPollRateLimitReleasesClaimWithoutIssuingToken() {
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.APPROVED, approvedRecord()));
        when(store.acquirePollSlot(anyString(), any())).thenReturn(false);

        assertError(this::poll, BabyLogErrorCode.DEVICE_BIND_RATE_LIMITED);
        verify(store).releaseClaim(eq(DEVICE_HASH), anyString());
        verify(authService, never()).loginByBoundDevice(anyLong(), any());
    }

    @Test
    void loginFailureReleasesClaimForLaterRetry() {
        DeviceBindRecord record = approvedRecord();
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.APPROVED, record));
        doThrow(new ServiceException("device conflict"))
                .when(authService).loginByBoundDevice(USER_ID, record.device());

        assertThatThrownBy(this::poll).isInstanceOf(ServiceException.class)
                .hasMessage("device conflict");
        verify(store).releaseClaim(eq(DEVICE_HASH), anyString());
        verify(store, never()).finishClaim(anyString(), anyString(), anyString());
    }

    @Test
    void lostFinalConsumptionRevokesIssuedTokensAndReturns8403() {
        DeviceBindRecord record = approvedRecord();
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.APPROVED, record));
        LoginTokenVO login = login();
        when(authService.loginByBoundDevice(USER_ID, record.device())).thenReturn(login);
        when(store.finishClaim(anyString(), anyString(), anyString())).thenReturn(false);

        assertError(this::poll, BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
        verify(authService).logout("refresh-token");
    }

    @Test
    void finalConsumptionRedisFailureAlsoRevokesIssuedTokens() {
        DeviceBindRecord record = approvedRecord();
        claim(new DeviceBindClaim(DeviceBindClaim.Outcome.APPROVED, record));
        when(authService.loginByBoundDevice(USER_ID, record.device())).thenReturn(login());
        when(store.finishClaim(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        assertError(this::poll, BabyLogErrorCode.DEVICE_BIND_ALREADY_CLAIMED);
        verify(authService).logout("refresh-token");
    }

    @Test
    void concurrentPollsHaveOnlyOneWinner() throws Exception {
        AtomicBoolean won = new AtomicBoolean();
        when(store.beginClaim(anyString(), anyString(), any())).thenAnswer(invocation -> {
            if (won.compareAndSet(false, true)) {
                return new DeviceBindClaim(DeviceBindClaim.Outcome.APPROVED, approvedRecord());
            }
            return new DeviceBindClaim(DeviceBindClaim.Outcome.BUSY, null);
        });
        when(authService.loginByBoundDevice(eq(USER_ID), any())).thenReturn(login());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<DeviceBindPollVO>> futures = new ArrayList<>();
            futures.add(executor.submit(this::poll));
            futures.add(executor.submit(this::poll));
            List<String> statuses = new ArrayList<>();
            for (Future<DeviceBindPollVO> future : futures) {
                statuses.add(future.get().getStatus());
            }
            assertThat(statuses).containsExactlyInAnyOrder("approved", "pending");
        } finally {
            executor.shutdownNow();
        }
        verify(authService, times(1)).loginByBoundDevice(anyLong(), any());
        verify(store, times(1)).finishClaim(anyString(), anyString(), anyString());
    }

    @Test
    void confirmBindsOnlySuppliedLoginIdentityAndClearsFailures() {
        when(store.resolveDeviceHash(anyString())).thenReturn(Optional.of(DEVICE_HASH));
        when(store.approve(anyString(), eq(DEVICE_HASH), eq(USER_ID))).thenReturn(true);

        var vo = service.confirm(USER_ID, userCodeCmd(), "10.0.0.8");

        assertThat(vo.getStatus()).isEqualTo("approved");
        verify(store).approve(DeviceBindHash.sha256("012345"), DEVICE_HASH, USER_ID);
        verify(store).clearConfirmFailures(eq(USER_ID), eq(DeviceBindHash.sha256("10.0.0.8")));
    }

    @Test
    void denyAlsoConsumesPendingUserCodeWithoutBindingUser() {
        when(store.resolveDeviceHash(anyString())).thenReturn(Optional.of(DEVICE_HASH));
        when(store.deny(anyString(), eq(DEVICE_HASH))).thenReturn(true);

        var vo = service.deny(USER_ID, userCodeCmd(), "10.0.0.8");

        assertThat(vo.getStatus()).isEqualTo("denied");
        verify(store, never()).approve(anyString(), anyString(), anyLong());
        verify(store).clearConfirmFailures(anyLong(), anyString());
    }

    @Test
    void invalidUserCodeIncreasesBothFailureDimensions() {
        when(store.resolveDeviceHash(anyString())).thenReturn(Optional.empty());
        String ipHash = DeviceBindHash.sha256("10.0.0.8");

        assertError(() -> service.confirm(USER_ID, userCodeCmd(), "10.0.0.8"),
                BabyLogErrorCode.DEVICE_BIND_CODE_INVALID);
        verify(store).increaseConfirmFailures(USER_ID, ipHash, Duration.ofSeconds(600));
        verify(store, never()).clearConfirmFailures(anyLong(), anyString());
    }

    @Test
    void confirmIsLockedByUserOrIpFailureWindow() {
        when(store.confirmUserFailures(USER_ID)).thenReturn(5L);
        assertError(() -> service.confirm(USER_ID, userCodeCmd(), "10.0.0.8"),
                BabyLogErrorCode.DEVICE_BIND_CONFIRM_LOCKED);
        when(store.confirmUserFailures(USER_ID)).thenReturn(0L);
        when(store.confirmIpFailures(anyString())).thenReturn(20L);
        assertError(() -> service.deny(USER_ID, userCodeCmd(), "10.0.0.8"),
                BabyLogErrorCode.DEVICE_BIND_CONFIRM_LOCKED);
        verify(store, never()).resolveDeviceHash(anyString());
    }

    private DeviceBindPollVO poll() {
        return service.poll(pollCmd());
    }

    private void claim(DeviceBindClaim value) {
        when(store.beginClaim(anyString(), anyString(), any())).thenReturn(value);
    }

    private static DeviceBindApplyCmd applyCmd() {
        DeviceBindApplyCmd cmd = new DeviceBindApplyCmd();
        cmd.setDevice(device());
        return cmd;
    }

    private static DeviceBindPollCmd pollCmd() {
        DeviceBindPollCmd cmd = new DeviceBindPollCmd();
        cmd.setDeviceCode("private-device-code");
        return cmd;
    }

    private static DeviceBindUserCodeCmd userCodeCmd() {
        DeviceBindUserCodeCmd cmd = new DeviceBindUserCodeCmd();
        cmd.setUserCode("012345");
        return cmd;
    }

    private static DeviceBindRecord approvedRecord() {
        return new DeviceBindRecord(DeviceBindRecord.APPROVED, device(), Long.toString(USER_ID));
    }

    private static DeviceInfo device() {
        DeviceInfo device = new DeviceInfo();
        device.setDeviceUuid(" tv-living-room ");
        device.setDeviceName("客厅电视");
        device.setPlatform("android_tv");
        device.setAppVersion("0.1.0");
        return device;
    }

    private static LoginTokenVO login() {
        LoginTokenVO vo = new LoginTokenVO();
        vo.setAccessToken("access-token");
        vo.setRefreshToken("refresh-token");
        vo.setUserId(USER_ID);
        vo.setDeviceId(7001L);
        vo.setWorkerId(3);
        return vo;
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ServiceException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected.getCode()));
    }
}
