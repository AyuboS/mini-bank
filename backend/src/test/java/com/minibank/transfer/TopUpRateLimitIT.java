package com.minibank.transfer;

import static com.minibank.support.TransferRequests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Uses the top-up rule exactly as application.yml defines it: 10 requests per 10 minutes. */
@DisplayName("Simulated top-up rate limit")
@TestPropertySource(properties = "minibank.rate-limit.enabled=true")
class TopUpRateLimitIT extends AbstractIntegrationTest {

    @Test
    void theEleventhTopUpInTenMinutesIsThrottled() {
        BankAccount account = data.fundedAccount(data.customer("alice"), "0.00");
        String token = api.loginAsTestUser("alice");
        String path = "/api/v1/accounts/" + account.getId() + "/top-up";
        Map<String, Object> body = Map.of("amount", new BigDecimal("1.00"));

        for (int i = 0; i < 10; i++) {
            assertThat(api.post(path, token, body, newKey()).getResponse().getStatus())
                    .as("top-up %d", i + 1)
                    .isEqualTo(201);
        }

        var throttled = api.post(path, token, body, newKey());
        assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
        assertThat(databaseCleaner.balanceOf(account.getAccountNumber())).isEqualByComparingTo("10.00");
    }
}
