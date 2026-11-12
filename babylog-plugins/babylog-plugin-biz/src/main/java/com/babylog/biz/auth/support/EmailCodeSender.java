package com.babylog.biz.auth.support;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.common.core.exception.ServiceException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * 验证码邮件发送器（真 SMTP）。
 *
 * <p>为什么不用 message 插件的 EmailChannel：它的契约是「按 sys_user 的 userId 投递」，
 * 且实现只写 {@code msg_send_log} 不真发——验证码的第一封信面对的是**尚未存在的账号**，
 * 两者语义根本不匹配；要让它真发又得改 message 插件（脚手架红线）。
 * 因此这里自建一条窄通道：只做「给一个裸邮箱地址发一封验证码」，与账号体系解耦。
 *
 * <p>{@link JavaMailSender} 由 Boot 的 {@code MailSenderAutoConfiguration} 在
 * {@code spring.mail.host} 有值时装配；未配置时不装配 ⇒ 这里用 ObjectProvider 探测，
 * 发码端点返回 8374 而不是启动失败（dev 未填 SMTP 也能起服、跑其他端点）。
 */
@Component
public class EmailCodeSender {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final BabyLogAuthProperties properties;
    private final String springMailUsername;

    public EmailCodeSender(ObjectProvider<JavaMailSender> mailSenderProvider,
                           BabyLogAuthProperties properties,
                           @Value("${spring.mail.username:}") String springMailUsername) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
        this.springMailUsername = springMailUsername;
    }

    /** 邮件通道是否就绪（host + 发件人都有值） */
    public boolean isConfigured() {
        return mailSenderProvider.getIfAvailable() != null && StringUtils.hasText(resolveFrom());
    }

    /**
     * 发送验证码邮件。
     *
     * @throws ServiceException 8374 未配置 / 8375 发送失败（两者对客户端都是「稍后再试」，仅便于排障区分）
     */
    public void sendCode(String to, String code, long ttlSeconds) {
        if (!isConfigured()) {
            log.warn("[BABYLOG][auth] 验证码邮件未发送：SMTP 未配置（spring.mail.host / 发件人缺失）");
            throw new ServiceException(BabyLogErrorCode.MAIL_NOT_CONFIGURED);
        }
        JavaMailSender sender = mailSenderProvider.getObject();
        long ttlMinutes = Math.max(1L, ttlSeconds / 60L);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(resolveFrom(), "BabyLog");
            helper.setTo(to);
            helper.setSubject(properties.getEmail().getSubject());
            helper.setText(buildHtml(code, ttlMinutes), true);
            sender.send(message);
            log.info("[BABYLOG][auth] 验证码邮件已发送 to={} ttlMinutes={}", maskEmail(to), ttlMinutes);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("[BABYLOG][auth] 验证码邮件发送失败 to={} cause={}", maskEmail(to), e.getMessage());
            throw new ServiceException(BabyLogErrorCode.MAIL_SEND_FAILED);
        }
    }

    /** 发件人：优先 babylog.auth.email.from，其次 spring.mail.username */
    private String resolveFrom() {
        String configured = properties.getEmail().getFrom();
        return StringUtils.hasText(configured) ? configured : springMailUsername;
    }

    private static String buildHtml(String code, long ttlMinutes) {
        return """
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#1f2329">
                  <h2 style="font-size:18px;margin:0 0 16px">BabyLog 登录验证码</h2>
                  <p style="font-size:14px;line-height:1.6;color:#4e5969;margin:0 0 16px">你正在登录 BabyLog，验证码如下：</p>
                  <p style="font-size:32px;letter-spacing:8px;font-weight:600;margin:0 0 16px;color:#1f2329">%s</p>
                  <p style="font-size:13px;line-height:1.6;color:#86909c;margin:0 0 8px">%d 分钟内有效，仅可使用一次。</p>
                  <p style="font-size:13px;line-height:1.6;color:#86909c;margin:0">若非本人操作，请忽略本邮件，你的账号不会有任何变化。</p>
                </div>
                """.formatted(code, ttlMinutes);
    }

    /** 日志脱敏：a***@example.com */
    private static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
