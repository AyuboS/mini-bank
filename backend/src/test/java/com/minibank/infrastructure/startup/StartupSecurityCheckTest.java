package com.minibank.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minibank.common.config.SecurityProperties;
import com.minibank.notification.config.NotificationProperties;
import com.minibank.notification.email.EmailService;
import com.minibank.notification.sms.SmsService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StartupSecurityCheckTest {

    private static final String UNIQUE_SECRET = "a-secret-that-is-not-in-the-repository-0123456789";
    private static final String PUBLISHED_SECRET = "dev-only-secret-do-not-use-anywhere-else-0123456789";
    private static final String ORIGIN = "https://mini-bank-silk.vercel.app";

    private static StartupSecurityCheck check(String profile, String secret, String origin, boolean exposeCodes) {
        SecurityProperties security = new SecurityProperties(
                new SecurityProperties.Jwt(secret, "mbank", Duration.ofMinutes(15), Duration.ofDays(7)),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new SecurityProperties.Cors(List.of(origin)));
        NotificationProperties notifications = new NotificationProperties(
                ORIGIN,
                exposeCodes,
                new NotificationProperties.Sms("dev", "MBank", "", ""),
                new NotificationProperties.Email("dev", "MBank <no-reply@mbank.local>"));
        SmsService sms = mock(SmsService.class);
        when(sms.isSimulated()).thenReturn(true);
        when(sms.providerName()).thenReturn("dev");
        EmailService email = mock(EmailService.class);
        when(email.isSimulated()).thenReturn(true);
        when(email.providerName()).thenReturn("dev");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new StartupSecurityCheck(security, notifications, sms, email, environment);
    }

    @Test
    void theDemoProfileStartsWithSimulatedMessages() {
        assertThatCode(() -> check("demo", UNIQUE_SECRET, ORIGIN, false).run(null)).doesNotThrowAnyException();
    }

    @Test
    void theDemoProfileRefusesThePublishedSecret() {
        assertThatThrownBy(() -> check("demo", PUBLISHED_SECRET, ORIGIN, false).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void theDemoProfileRefusesCodesInResponses() {
        assertThatThrownBy(() -> check("demo", UNIQUE_SECRET, ORIGIN, true).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expose-codes");
    }

    @Test
    void theDemoProfileRefusesAWildcardOrigin() {
        assertThatThrownBy(() -> check("demo", UNIQUE_SECRET, "*", false).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void productionStillRefusesSimulatedMessages() {
        assertThatThrownBy(() -> check("prod", UNIQUE_SECRET, ORIGIN, false).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS provider");
    }

    @Test
    void developmentOnlyWarns() {
        assertThatCode(() -> check("dev", PUBLISHED_SECRET, "*", true).run(null)).doesNotThrowAnyException();
    }
}
