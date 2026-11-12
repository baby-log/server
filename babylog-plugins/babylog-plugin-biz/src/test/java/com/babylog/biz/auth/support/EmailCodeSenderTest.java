package com.babylog.biz.auth.support;

import com.babylog.biz.api.enums.BabyLogErrorCode;
import com.babylog.biz.auth.config.BabyLogAuthProperties;
import com.babylog.common.core.exception.ServiceException;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码邮件发送器单测：**全程 Mock，不连 SMTP、不发真信**。
 *
 * <p>断言重点是「信的内容对不对」（收件人/主题/正文带码）与「没配置时的降级」，
 * 真正的投递能力只能在 dev 用真邮箱验证一次（见 PROGRESS 的手动清单）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailCodeSenderTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    private BabyLogAuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BabyLogAuthProperties();
        properties.getEmail().setFrom("no-reply@babylog.test");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSenderProvider.getObject()).thenReturn(mailSender);
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendCode_buildsMessageWithCodeAndSubject() throws Exception {
        EmailCodeSender sender = new EmailCodeSender(mailSenderProvider, properties, "");

        sender.sendCode("mom@example.com", "123456", 300L);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("mom@example.com");
        assertThat(message.getSubject()).isEqualTo("BabyLog 登录验证码");
        assertThat(extractText(message)).contains("123456").contains("5 分钟内有效");
    }

    @Test
    void sendCode_fallsBackToSpringMailUsernameAsFrom() {
        EmailCodeSender sender = new EmailCodeSender(mailSenderProvider, new BabyLogAuthProperties(),
                "fallback@example.com");

        assertThat(sender.isConfigured()).isTrue();
    }

    @Test
    void sendCode_rejectsWhenSmtpNotConfigured() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        EmailCodeSender sender = new EmailCodeSender(mailSenderProvider, new BabyLogAuthProperties(), "");

        assertThat(sender.isConfigured()).isFalse();
        assertThatThrownBy(() -> sender.sendCode("mom@example.com", "123456", 300L))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BabyLogErrorCode.MAIL_NOT_CONFIGURED.getCode()));
    }

    @Test
    void sendCode_mapsTransportFailureToMailSendFailed() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        EmailCodeSender sender = new EmailCodeSender(mailSenderProvider, properties, "");

        assertThatThrownBy(() -> sender.sendCode("mom@example.com", "123456", 300L))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BabyLogErrorCode.MAIL_SEND_FAILED.getCode()));
    }

    /** 递归取正文（setText(html, true) 包成 related 多段结构） */
    private static String extractText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                builder.append(extractText(bodyPart));
            }
            return builder.toString();
        }
        return content == null ? "" : content.toString();
    }
}
