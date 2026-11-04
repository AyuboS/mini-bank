package com.minibank.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/**
 * The time-dependent half of verification: expiry, and the resend cooldown.
 *
 * <p>Its own class because both need windows measured in milliseconds, and a property override
 * forks the Spring context. Driving the real clock rather than injecting a fake one is deliberate:
 * expiry is compared against {@code Clock} in three separate places, and a fake would prove only
 * that the fake agrees with itself.
 */
@TestPropertySource(
        properties = {
            "minibank.verification.phone.code-ttl=1s",
            // Otherwise the resend needed to get a second code is refused by the cooldown.
            "minibank.verification.phone.resend-cooldown=1ms",
            "minibank.verification.email.resend-cooldown=1ms"
        })
class VerificationExpiryIT extends AbstractIntegrationTest {

    private static final String CODE_PATH = "/api/v1/auth/phone/code";
    private static final String LOGIN_PATH = "/api/v1/auth/phone/login";
    private static final String PHONE = "+998905550001";

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("an expired code is reported as expired, not as wrong")
    void refusesAnExpiredCode() throws InterruptedException {
        var user = data.customer("expiry.alice");
        user.confirmPhoneNumber(PHONE, java.time.Instant.now());
        userRepository.save(user);

        String code = api.parse(api.post(CODE_PATH, null, Map.of("phoneNumber", PHONE, "purpose", "LOGIN")))
                .get("developmentCode")
                .asText();

        Thread.sleep(1200);

        var result = api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", code));
        // 410 Gone, and a distinct code: "wrong" and "too late" call for different words on screen,
        // and reporting the second as the first sends people hunting for a typo that is not there.
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.GONE.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("VERIFICATION_CODE_EXPIRED");
    }

    @Test
    @DisplayName("once the cooldown elapses the link can be sent again")
    void resendsAfterTheCooldown() {
        api.post(
                "/api/v1/auth/register",
                null,
                Map.of(
                        "username", "expiry.resend",
                        "email", "expiry.resend@example.com",
                        "password", com.minibank.support.TestDataFactory.PASSWORD,
                        "firstName", "Re",
                        "lastName", "Send"));
        emails.clear();

        String token = api.login("expiry.resend", com.minibank.support.TestDataFactory.PASSWORD);
        var result = api.post("/api/v1/users/me/email/verify", token, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(emails.lastTokenFor("expiry.resend@example.com")).isPresent();
    }

    @Test
    @DisplayName("expiry does not consume one of the customer's attempts")
    void doesNotChargeAnAttemptForExpiry() throws InterruptedException {
        var user = data.customer("expiry.bob");
        user.confirmPhoneNumber("+998905550002", java.time.Instant.now());
        userRepository.save(user);

        api.post(CODE_PATH, null, Map.of("phoneNumber", "+998905550002", "purpose", "LOGIN"));
        Thread.sleep(1200);
        api.post(LOGIN_PATH, null, Map.of("phoneNumber", "+998905550002", "code", "123456"));

        // A fresh code still gets the full allowance: the expired one's attempts are irrelevant.
        String fresh = api.parse(api.post(
                        CODE_PATH, null, Map.of("phoneNumber", "+998905550002", "purpose", "LOGIN")))
                .get("developmentCode")
                .asText();
        assertThat(api.post(LOGIN_PATH, null, Map.of("phoneNumber", "+998905550002", "code", fresh))
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }
}
