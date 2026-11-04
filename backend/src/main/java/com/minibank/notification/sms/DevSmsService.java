package com.minibank.notification.sms;

import com.minibank.notification.config.NotificationProperties;
import com.minibank.verification.service.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * DEVELOPMENT ONLY. Pretends to send an SMS.
 *
 * <p>Selected whenever {@code minibank.notifications.sms.provider} is anything other than a real
 * provider, which is the default, so a fresh checkout runs without an account anywhere.
 *
 * <p>It writes the code to the application log on purpose — that is how a developer completes a
 * sign-in locally — and that is precisely why it must never run in production. {@code
 * StartupSecurityCheck} refuses to start a prod instance configured this way, rather than leaving
 * the guarantee to a comment.
 */
@Service
@ConditionalOnProperty(name = "minibank.notifications.sms.provider", havingValue = "dev", matchIfMissing = true)
public class DevSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(DevSmsService.class);

    private final NotificationProperties properties;

    public DevSmsService(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String toE164, String message) {
        log.warn(
                """

                ┌─────────────────────────────────────────────────────────────┐
                │  DEVELOPMENT ONLY — no SMS was sent                         │
                ├─────────────────────────────────────────────────────────────┤
                  to:   {}
                  from: {}

                {}
                └─────────────────────────────────────────────────────────────┘
                """,
                PhoneNumbers.mask(toE164),
                properties.sms().from(),
                message.indent(2));
    }

    @Override
    public String providerName() {
        return "dev";
    }

    @Override
    public boolean isSimulated() {
        return true;
    }
}
