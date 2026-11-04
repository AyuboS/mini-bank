package com.minibank.transfer;

import static com.minibank.support.TransferRequests.newKey;
import static com.minibank.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Transfer limits, with thresholds set low enough that a test can actually reach them. */
@DisplayName("Transfer limits")
@TestPropertySource(
        properties = {
            "minibank.limits.per-transfer-max=200.00",
            "minibank.limits.daily-max=500.00",
            "minibank.limits.max-transfers-per-hour=4"
        })
class TransferLimitIT extends AbstractIntegrationTest {

    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;
    private String adminToken;
    private User alice;

    @BeforeEach
    void openAccounts() {
        alice = data.customer("alice");
        User bob = data.customer("bob");
        data.admin("root");
        aliceAccount = data.fundedAccount(alice, "5000.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
        adminToken = api.loginAsTestUser("root");
    }

    private Map<String, Object> body(String amount) {
        return transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), amount);
    }

    @Test
    void aTransferAboveThePerTransferCeilingIsRejected() {
        var result = api.post("/api/v1/transfers", aliceToken, body("200.01"), newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("TRANSFER_LIMIT_EXCEEDED");
    }

    @Test
    void aTransferExactlyAtThePerTransferCeilingIsAllowed() {
        var result = api.post("/api/v1/transfers", aliceToken, body("200.00"), newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void theDailyCeilingAccumulatesAcrossTransfers() {
        api.post("/api/v1/transfers", aliceToken, body("200.00"), newKey());
        api.post("/api/v1/transfers", aliceToken, body("200.00"), newKey());

        var result = api.post("/api/v1/transfers", aliceToken, body("150.00"), newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("message").asText()).contains("daily limit");
    }

    @Test
    void theHourlyTransferCountIsEnforced() {
        for (int i = 0; i < 4; i++) {
            api.post("/api/v1/transfers", aliceToken, body("1.00"), newKey());
        }

        var result = api.post("/api/v1/transfers", aliceToken, body("1.00"), newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("message").asText()).contains("per hour");
    }

    @Test
    void anAdministratorCanRaiseTheLimitsForOneCustomer() throws Exception {
        var updated = api.put(
                "/api/v1/admin/users/" + alice.getId() + "/limits",
                adminToken,
                Map.of("perTransferMax", "2000.00", "dailyMax", "5000.00", "note", "Verified business customer"));
        assertThat(updated.getResponse().getStatus())
                .as("body: %s", updated.getResponse().getContentAsString())
                .isEqualTo(200);

        var result = api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void theEffectiveLimitsAreReportedToAdministrators() {
        var before = api.parse(api.get("/api/v1/admin/users/" + alice.getId() + "/limits", adminToken));
        assertThat(before.get("customised").asBoolean()).isFalse();
        assertThat(before.get("perTransferMax").decimalValue()).isEqualByComparingTo("200.00");

        api.put(
                "/api/v1/admin/users/" + alice.getId() + "/limits",
                adminToken,
                Map.of("perTransferMax", "999.00"));

        var after = api.parse(api.get("/api/v1/admin/users/" + alice.getId() + "/limits", adminToken));
        assertThat(after.get("customised").asBoolean()).isTrue();
        assertThat(after.get("perTransferMax").decimalValue()).isEqualByComparingTo("999.00");
        assertThat(after.get("dailyMax").decimalValue())
                .as("an unset override field falls back to the platform default")
                .isEqualByComparingTo("500.00");
    }

    @Test
    void aCustomerCannotChangeTheirOwnLimits() {
        var result = api.put(
                "/api/v1/admin/users/" + alice.getId() + "/limits", aliceToken, Map.of("perTransferMax", "999999.00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
