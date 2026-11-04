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

/**
 * The risk engine end to end, with thresholds low enough to be reached.
 *
 * <p>The point of REVIEW is that it is not a rejection: the transfer exists, in PENDING, and no
 * money has moved. These tests pin down both halves of that, because a "held" transfer that
 * quietly moved money would be worse than no risk engine at all.
 */
@DisplayName("Risk engine and manual review")
@TestPropertySource(
        properties = {
            "minibank.risk.large-amount.threshold=1000.00",
            "minibank.risk.large-amount.score=60",
            "minibank.risk.thresholds.review=50",
            "minibank.risk.thresholds.block=85",
            "minibank.risk.new-beneficiary.amount-threshold=1000.00",
            "minibank.risk.new-beneficiary.score=30"
        })
class RiskReviewIT extends AbstractIntegrationTest {

    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;
    private String adminToken;

    @BeforeEach
    void openAccounts() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        data.admin("root");
        aliceAccount = data.fundedAccount(alice, "10000.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
        adminToken = api.loginAsTestUser("root");
    }

    private Map<String, Object> body(String amount) {
        return transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), amount);
    }

    @Test
    void anOrdinaryTransferIsAllowedStraightThrough() {
        var result = api.post("/api/v1/transfers", aliceToken, body("100.00"), newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(api.parse(result).get("risk").get("decision").asText()).isEqualTo("ALLOW");
    }

    @Test
    void aLargeTransferIsHeldForReviewAndNoMoneyMoves() {
        var result = api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey());

        assertThat(result.getResponse().getStatus()).as("202 Accepted, not 201 Created").isEqualTo(202);
        var body = api.parse(result);
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("risk").get("decision").asText()).isEqualTo("REVIEW");
        assertThat(body.get("risk").get("reasons").get(0).asText()).contains("1500");

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber()))
                .as("a held transfer must not move money")
                .isEqualByComparingTo("10000.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("0");
    }

    @Test
    void anAdministratorCanApproveAHeldTransferAndTheMoneyThenMoves() {
        var held = api.parse(api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey()));

        var approved = api.post("/api/v1/admin/transfers/" + held.get("id").asText() + "/approve", adminToken, null);

        assertThat(approved.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(approved).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("8500.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("1500.00");
    }

    @Test
    void anAdministratorCanRejectAHeldTransferAndNoMoneyMoves() {
        var held = api.parse(api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey()));

        var rejected = api.post(
                "/api/v1/admin/transfers/" + held.get("id").asText() + "/reject",
                adminToken,
                Map.of("reason", "Customer could not be reached"));

        assertThat(rejected.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(rejected).get("status").asText()).isEqualTo("FAILED");
        assertThat(api.parse(rejected).get("failureReason").asText()).contains("could not be reached");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("10000.00");
    }

    @Test
    void aHeldTransferCannotBeApprovedTwice() {
        var held = api.parse(api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey()));
        String path = "/api/v1/admin/transfers/" + held.get("id").asText() + "/approve";

        api.post(path, adminToken, null);
        var second = api.post(path, adminToken, null);

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(second).get("code").asText()).isEqualTo("INVALID_TRANSFER_STATE");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber()))
                .as("approving twice must not pay twice")
                .isEqualByComparingTo("1500.00");
    }

    @Test
    void aRejectedTransferCannotThenBeApproved() {
        var held = api.parse(api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey()));
        String id = held.get("id").asText();
        api.post("/api/v1/admin/transfers/" + id + "/reject", adminToken, Map.of("reason", "Suspicious"));

        var approved = api.post("/api/v1/admin/transfers/" + id + "/approve", adminToken, null);

        assertThat(approved.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void aCustomerCannotApproveTheirOwnHeldTransfer() {
        var held = api.parse(api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey()));

        var result = api.post("/api/v1/admin/transfers/" + held.get("id").asText() + "/approve", aliceToken, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("0");
    }

    @Test
    void aHeldTransferStillCountsTowardsWhatTheCustomerHasCommitted() {
        // Money awaiting review has already been requested by the customer; excluding it from the
        // daily total would let somebody exceed their ceiling by queueing transfers.
        api.post("/api/v1/transfers", aliceToken, body("1500.00"), newKey());

        var pending = api.parse(api.get("/api/v1/transactions?status=PENDING", aliceToken));

        assertThat(pending.get("content")).hasSize(1);
    }

    @Test
    void combinedSignalsPushATransferPastTheBlockThreshold() {
        // Large amount (60) plus a payment to a payee added moments ago (30) exceeds 85.
        api.post(
                "/api/v1/beneficiaries",
                aliceToken,
                Map.of("accountNumber", bobAccount.getAccountNumber(), "nickname", "Brand new payee"));

        var result = api.post("/api/v1/transfers", aliceToken, body("2000.00"), newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("RISK_BLOCKED");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("10000.00");
    }

    @Test
    void aBlockedTransferIsStillRecordedSoTheAttemptIsVisible() {
        api.post(
                "/api/v1/beneficiaries",
                aliceToken,
                Map.of("accountNumber", bobAccount.getAccountNumber(), "nickname", "Brand new payee"));
        api.post("/api/v1/transfers", aliceToken, body("2000.00"), newKey());

        var blocked = api.parse(api.get("/api/v1/admin/transfers?status=FAILED", adminToken));

        assertThat(blocked.get("content")).hasSize(1);
        assertThat(blocked.get("content").get(0).get("failureReason").asText()).isNotEmpty();
    }
}
