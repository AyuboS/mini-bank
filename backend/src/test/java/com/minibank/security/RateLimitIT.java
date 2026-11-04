package com.minibank.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.support.AbstractIntegrationTest;
import com.minibank.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Rate limiting on the endpoints worth attacking.
 *
 * <p>Enabled explicitly here, and disabled elsewhere, so that unrelated tests are not throttled by
 * a shared budget and this one is not at the mercy of what ran before it.
 */
@DisplayName("Rate limiting")
@TestPropertySource(
        properties = {
            "minibank.rate-limit.enabled=true",
            "minibank.rate-limit.rules[0].name=login",
            "minibank.rate-limit.rules[0].path-pattern=/api/v1/auth/login",
            "minibank.rate-limit.rules[0].methods[0]=POST",
            "minibank.rate-limit.rules[0].capacity=3",
            "minibank.rate-limit.rules[0].refill-period=1m"
        })
class RateLimitIT extends AbstractIntegrationTest {

    @Test
    void repeatedLoginAttemptsAreThrottledBeforeTheyBecomeBruteForce() {
        data.customer("alice");

        // The bucket holds three; the fourth attempt is refused by the limiter rather than by
        // credential checking.
        for (int i = 0; i < 3; i++) {
            assertThat(api.attemptLogin("alice", "WrongPassword" + i + "!"))
                    .as("attempt %d is answered on its merits", i + 1)
                    .isIn(401, 423);
        }

        assertThat(api.attemptLogin("alice", "WrongPasswordAgain1!")).isEqualTo(429);
    }

    @Test
    void aThrottledResponseTellsTheClientWhenToComeBack() {
        data.customer("alice");
        for (int i = 0; i < 4; i++) {
            api.attemptLogin("alice", "WrongPassword" + i + "!");
        }

        var result = api.post(
                "/api/v1/auth/login",
                null,
                new com.minibank.support.ApiClient.Credentials("alice", TestDataFactory.PASSWORD));

        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(result.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(api.parse(result).get("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void endpointsWithoutARuleAreNotThrottled() {
        data.customer("alice");
        String token = api.loginAsTestUser("alice");

        for (int i = 0; i < 25; i++) {
            assertThat(api.get("/api/v1/accounts", token).getResponse().getStatus())
                    .isEqualTo(200);
        }
    }
}
