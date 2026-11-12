package com.babylog.biz.auth.service.impl;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.biz.auth.service.EmailCodeService;
import com.babylog.biz.auth.store.EmailCodeStore;
import com.babylog.biz.auth.support.EmailCodeSender;
import com.babylog.biz.auth.vo.CodeSendVO;
import com.babylog.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 邮箱验证码实现。
 *
 * <p>顺序有讲究，每一步都在替下一步挡子弹：
 * <ol>
 *   <li>格式校验（8373）——脏输入不进 Redis，限流键不被垃圾数据污染；</li>
 *   <li>通道就绪校验（8374）——没配 SMTP 时**先失败**，不消耗当日配额；</li>
 *   <li>冷却占位（8371）——SETNX 原子占位，并发请求只有一个能过；</li>
 *   <li>日配额（8372）——防止换着分钟数刷；</li>
 *   <li>发信失败（8375）——码已写库但没发出去，回滚删除，冷却保留（不给连点重试的理由）。</li>
 * </ol>
 *
 * <p>校验侧：错一次计一次，累计到 {@code maxVerifyAttempts} 直接作废验证码（防止 6 位数字被穷举），
 * 正确即消费（单次有效）。比较用定长比较，避免时序侧信道。
 */
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeServiceImpl.class);

    /** 宽松校验：只挡明显脏数据，不追求 RFC 完备（过于严格反而误杀合法地址） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);

    private final EmailCodeStore codeStore;
    private final EmailCodeSender codeSender;
    private final BabyLogAuthProperties properties;

    public EmailCodeServiceImpl(EmailCodeStore codeStore, EmailCodeSender codeSender,
                                BabyLogAuthProperties properties) {
        this.codeStore = codeStore;
        this.codeSender = codeSender;
        this.properties = properties;
    }

    @Override
    public CodeSendVO sendLoginCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ServiceException(BabyLogErrorCode.EMAIL_FORMAT_INVALID);
        }
        BabyLogAuthProperties.Email rule = properties.getEmail();
        if (!codeSender.isConfigured()) {
            throw new ServiceException(BabyLogErrorCode.MAIL_NOT_CONFIGURED);
        }
        if (!codeStore.acquireSendSlot(email, Duration.ofSeconds(rule.getResendIntervalSeconds()))) {
            throw new ServiceException(BabyLogErrorCode.EMAIL_CODE_TOO_FREQUENT);
        }
        long todayCount = codeStore.increaseDailyCount(email, DAILY_WINDOW);
        if (todayCount > rule.getDailyLimit()) {
            throw new ServiceException(BabyLogErrorCode.EMAIL_DAILY_LIMIT);
        }

        String code = randomCode();
        Duration ttl = Duration.ofSeconds(rule.getCodeTtlSeconds());
        codeStore.saveCode(email, code, ttl);
        codeStore.clearFailCount(email);
        try {
            codeSender.sendCode(email, code, rule.getCodeTtlSeconds());
        } catch (ServiceException e) {
            // 发信失败：码不留在库里（用户收不到，留着只会给撞码者可乘之机）
            codeStore.removeCode(email);
            throw e;
        }
        log.info("[BABYLOG][auth] 验证码已生成 email={} dailyCount={}", maskEmail(email), todayCount);

        CodeSendVO vo = new CodeSendVO();
        vo.setExpireSeconds(rule.getCodeTtlSeconds());
        vo.setResendAfterSeconds(rule.getResendIntervalSeconds());
        return vo;
    }

    @Override
    public void verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        if (!EMAIL_PATTERN.matcher(email).matches() || code == null || code.isBlank()) {
            throw new ServiceException(BabyLogErrorCode.EMAIL_CODE_INVALID);
        }
        Optional<String> stored = codeStore.getCode(email);
        if (stored.isEmpty()) {
            throw new ServiceException(BabyLogErrorCode.EMAIL_CODE_INVALID);
        }
        BabyLogAuthProperties.Email rule = properties.getEmail();
        if (!constantTimeEquals(stored.get(), code.trim())) {
            long fails = codeStore.increaseFailCount(email, Duration.ofSeconds(rule.getCodeTtlSeconds()));
            if (fails >= rule.getMaxVerifyAttempts()) {
                // 输错超限：码作废，用户必须重新获取（重新获取会清零错误计数）
                codeStore.removeCode(email);
                codeStore.clearFailCount(email);
                log.warn("[BABYLOG][auth] 验证码错误次数超限已作废 email={} attempts={}", maskEmail(email), fails);
            }
            throw new ServiceException(BabyLogErrorCode.EMAIL_CODE_INVALID);
        }
        codeStore.removeCode(email);
        codeStore.clearFailCount(email);
    }

    @Override
    public String normalize(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 6 位数字，SecureRandom（不用 Math.random：可预测） */
    private static String randomCode() {
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 日志脱敏：a***@example.com */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
