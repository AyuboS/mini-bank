package com.minibank.infrastructure.startup;

import com.minibank.common.config.SecurityProperties;
import com.minibank.notification.config.NotificationProperties;
import com.minibank.notification.email.EmailService;
import com.minibank.notification.sms.SmsService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production instance that is configured like a development one.
 *
 * <p>The {@code demo} profile, for a public demo of the simulation, gets the same checks except
 * one: it may simulate SMS and email, because a demo has nothing to deliver them with.
 *
 * <p>Documenting "change the secret before deploying" achieves nothing on the day somebody forgets.
 * These checks turn that class of mistake into a startup failure, which is impossible to miss and
 * impossible to ship past.
 */
@Component
public class StartupSecurityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityCheck.class);

    /** Secrets that appear in this repository and must therefore never protect real data. */
    private static final List<String> PUBLIC_SECRETS = List.of(
            "dev-only-secret-do-not-use-anywhere-else-0123456789",
            "test-only-secret-value-for-integration-tests-0123456789");

    private final SecurityProperties securityProperties;
    private final NotificationProperties notificationProperties;
    private final SmsService smsService;
    private final EmailService emailService;
    private final Environment environment;

    public StartupSecurityCheck(
            SecurityProperties securityProperties,
            NotificationProperties notificationProperties,
            SmsService smsService,
            EmailService emailService,
            Environment environment) {
        this.securityProperties = securityProperties;
        this.notificationProperties = notificationProperties;
        this.smsService = smsService;
        this.emailService = emailService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> profiles = List.of(environment.getActiveProfiles());
        boolean production = profiles.contains("prod");
        boolean strict = production || profiles.contains("demo");
        String secret = securityProperties.jwt().secret();

        if (PUBLIC_SECRETS.contains(secret)) {
            if (strict) {
                throw new IllegalStateException(
                        "JWT_SECRET is set to a value published in the source repository. "
                                + "Set a unique secret of at least 32 characters before deploying.");
            }
            log.warn("Using a development JWT secret. This is fine locally and fatal in production.");
        }

        if (strict && securityProperties.cors().allowedOrigins().contains("*")) {
            throw new IllegalStateException(
                    "CORS allowed-origins contains '*', which cannot be combined with credentials. "
                            + "List the exact origins that are permitted.");
        }

        checkMessageDelivery(production, strict);
    }

    /**
     * Stops a production instance that cannot actually deliver a verification code, or that would
     * hand one back through the API.
     *
     * <p>Both failure modes are silent by nature. A simulated SMS provider in production means every
     * customer sits on an OTP screen waiting for a message that was only ever written to a log; the
     * exposed-code flag means the log is the least of the problem, because the code is in the HTTP
     * response and the OTP has stopped being a second factor at all.
     */
    private void checkMessageDelivery(boolean production, boolean strict) {
        if (notificationProperties.exposeCodes()) {
            if (strict) {
                throw new IllegalStateException(
                        "minibank.notifications.expose-codes is enabled. This returns one-time codes in "
                                + "API responses and must never be set outside development.");
            }
            log.warn("Verification codes are exposed in API responses. Development only.");
        }

        if (smsService.isSimulated()) {
            if (production) {
                throw new IllegalStateException(
                        "The SMS provider is '%s', which does not send anything. Configure "
                                .formatted(smsService.providerName())
                                + "minibank.notifications.sms.provider before starting in prod.");
            }
            log.warn("SMS provider '{}' does not send messages; codes are written to this log.",
                    smsService.providerName());
        }

        if (emailService.isSimulated()) {
            if (production) {
                throw new IllegalStateException(
                        "The email provider is '%s', which does not send anything. Configure "
                                .formatted(emailService.providerName())
                                + "minibank.notifications.email.provider before starting in prod.");
            }
            log.warn("Email provider '{}' does not send messages; links are written to this log.",
                    emailService.providerName());
        }
    }
}
