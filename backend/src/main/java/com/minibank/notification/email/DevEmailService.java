package com.minibank.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * DEVELOPMENT ONLY. Writes the email to the log instead of sending it.
 *
 * <p>The plain-text alternative is what gets logged, not the HTML: it contains the same
 * verification link and is the part a person can actually read in a terminal.
 */
@Service
@ConditionalOnProperty(name = "minibank.notifications.email.provider", havingValue = "dev", matchIfMissing = true)
public class DevEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DevEmailService.class);

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        log.warn(
                """

                ┌─────────────────────────────────────────────────────────────┐
                │  DEVELOPMENT ONLY — no email was sent                       │
                ├─────────────────────────────────────────────────────────────┤
                  to:      {}
                  subject: {}

                {}
                └─────────────────────────────────────────────────────────────┘
                """,
                to,
                subject,
                textBody.indent(2));
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
