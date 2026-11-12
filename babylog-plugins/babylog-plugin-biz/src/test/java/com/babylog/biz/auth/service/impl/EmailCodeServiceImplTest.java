package com.babylog.biz.auth.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.biz.auth.store.EmailCodeStore;
import com.babylog.biz.auth.support.EmailCodeSender;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码链路单测：发码限流（冷却/日配额）、通道未配置、发信失败回滚、校验与错误作废。
 *
 * <p>存储用内存假实现（{@link FakeCodeStore}），邮件发送器全 Mock——**不真发任何邮件**。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailCodeServiceImplTest {

    @Mock
    private EmailCodeSender codeSender;

    private FakeCodeStore store;
    private BabyLogAuthProperties properties;
    private EmailCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        store = new FakeCodeStore();
        properties = new BabyLogAuthProperties();
        service = new EmailCodeServiceImpl(store, codeSender, properties);
        when(codeSender.isConfigured()).thenReturn(true);
    }

    @Test
    void sendLoginCode_storesSixDigitCodeAndReturnsPolicy() {
        CodeSendVO vo = service.sendLoginCode("  Someone@Example.COM ");

        // 邮箱归一化后入键：大小写/空格不得绕过限流
        assertThat(store.code).containsKey("someone@example.com");
        assertThat(store.code.get("someone@example.com")).hasSize(6).matches("\\d{6}");
        assertThat(vo.getExpireSeconds()).isEqualTo(300L);
        assertThat(vo.getResendAfterSeconds()).isEqualTo(60L);
        verify(codeSender).sendCode(eq("someone@example.com"), anyString(), eq(300L));
    }

    @Test
    void sendLoginCode_rejectsWhenCooldownActive() {
        store.cooldownActive = true;

        assertBizError(() -> service.sendLoginCode("someone@example.com"),
                BabyLogErrorCode.EMAIL_CODE_TOO_FREQUENT);
        verify(codeSender, never()).sendCode(anyString(), anyString(), anyLong());
    }

    @Test
    void sendLoginCode_rejectsWhenDailyLimitExceeded() {
        properties.getEmail().setDailyLimit(2);
        store.dailyCount = 2;

        assertBizError(() -> service.sendLoginCode("someone@example.com"),
                BabyLogErrorCode.EMAIL_DAILY_LIMIT);
    }

    @Test
    void sendLoginCode_rejectsInvalidEmailBeforeTouchingRedis() {
        assertBizError(() -> service.sendLoginCode("not-an-email"),
                BabyLogErrorCode.EMAIL_FORMAT_INVALID);
        // 脏输入不该消耗冷却/配额
        assertThat(store.cooldownActive).isFalse();
        assertThat(store.dailyCount).isZero();
    }

    @Test
    void sendLoginCode_reportsMailNotConfiguredWithoutConsumingQuota() {
        when(codeSender.isConfigured()).thenReturn(false);

        assertBizError(() -> service.sendLoginCode("someone@example.com"),
                BabyLogErrorCode.MAIL_NOT_CONFIGURED);
        assertThat(store.dailyCount).isZero();
        assertThat(store.cooldownActive).isFalse();
    }

    @Test
    void sendLoginCode_removesCodeWhenSendingFails() {
        doThrow(new ServiceException(BabyLogErrorCode.MAIL_SEND_FAILED))
                .when(codeSender).sendCode(anyString(), anyString(), anyLong());

        assertBizError(() -> service.sendLoginCode("someone@example.com"),
                BabyLogErrorCode.MAIL_SEND_FAILED);
        // 用户收不到的信，码不能留在库里
        assertThat(store.code).doesNotContainKey("someone@example.com");
    }

    @Test
    void verify_consumesCodeOnSuccess() {
        store.code.put("someone@example.com", "123456");

        assertThatCode(() -> service.verify("Someone@Example.com", " 123456 "))
                .doesNotThrowAnyException();
        assertThat(store.code).doesNotContainKey("someone@example.com");
    }

    @Test
    void verify_countsWrongAttemptsAndKeepsCodeUnderLimit() {
        store.code.put("someone@example.com", "123456");

        assertBizError(() -> service.verify("someone@example.com", "000000"),
                BabyLogErrorCode.EMAIL_CODE_INVALID);
        assertThat(store.failCount).isEqualTo(1L);
        assertThat(store.code).containsKey("someone@example.com");
    }

    @Test
    void verify_invalidatesCodeAfterMaxAttempts() {
        properties.getEmail().setMaxVerifyAttempts(3);
        store.code.put("someone@example.com", "123456");
        store.failCount = 2L;

        assertBizError(() -> service.verify("someone@example.com", "000000"),
                BabyLogErrorCode.EMAIL_CODE_INVALID);
        // 第 3 次错误：码作废，必须重新获取
        assertThat(store.code).doesNotContainKey("someone@example.com");
        assertThat(store.failCount).isZero();
    }

    @Test
    void verify_rejectsMissingCode() {
        assertBizError(() -> service.verify("someone@example.com", "123456"),
                BabyLogErrorCode.EMAIL_CODE_INVALID);
    }

    @Test
    void normalize_trimsAndLowercases() {
        assertThat(service.normalize("  A@B.CoM ")).isEqualTo("a@b.com");
        assertThat(service.normalize(null)).isEmpty();
    }

    private static void assertBizError(Runnable action, BabyLogErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(expected.getCode()));
    }

    /** 内存版验证码存储：只实现本测试关心的语义，不模拟过期（过期由 Redis TTL 保证） */
    private static final class FakeCodeStore implements EmailCodeStore {

        private final Map<String, String> code = new HashMap<>();
        private boolean cooldownActive;
        private long dailyCount;
        private long failCount;

        @Override
        public void saveCode(String email, String value, Duration ttl) {
            code.put(email, value);
        }

        @Override
        public Optional<String> getCode(String email) {
            return Optional.ofNullable(code.get(email));
        }

        @Override
        public void removeCode(String email) {
            code.remove(email);
        }

        @Override
        public boolean acquireSendSlot(String email, Duration cooldown) {
            if (cooldownActive) {
                return false;
            }
            cooldownActive = true;
            return true;
        }

        @Override
        public long increaseDailyCount(String email, Duration window) {
            return ++dailyCount;
        }

        @Override
        public long increaseFailCount(String email, Duration window) {
            return ++failCount;
        }

        @Override
        public void clearFailCount(String email) {
            failCount = 0L;
        }
    }
}
