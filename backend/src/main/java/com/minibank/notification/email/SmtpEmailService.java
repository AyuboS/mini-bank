package com.minibank.notification.email;

import com.minibank.notification.config.NotificationProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends through whatever SMTP relay {@code spring.mail.*} points at.
 *
 * <p>SMTP rather than a specific vendor's API, because every hosted provider worth using — Resend,
 * SendGrid, Mailgun, SES — offers an SMTP endpoint. That makes this one implementation cover all of
 * them through configuration alone, and a provider that justifies its own HTTP client can be added
 * later as a second {@link EmailService} without touching a single caller.
 *
 * <p>Multipart: the HTML part is what most clients show, and the plain text alternative is a real
 * fallback rather than a copy of the markup.
 */
@Service
@ConditionalOnProperty(name = "minibank.notifications.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailService(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.email().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.send(message);
            log.info("Verification email sent to {}", to);
        } catch (MailException | jakarta.mail.MessagingException ex) {
            throw new EmailDeliveryException("The email could not be sent", ex);
        }
    }

    @Override
    public String providerName() {
        return "smtp";
    }
}
