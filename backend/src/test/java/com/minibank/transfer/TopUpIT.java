package com.minibank.transfer;

import static com.minibank.support.TransferRequests.key;
import static com.minibank.support.TransferRequests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.AccountPurpose;
import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.common.money.CurrencyCode;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** Caps come from application.yml: 1000.00 per top-up and 2000.00 a day. */
@DisplayName("Simulated top-up")
class TopUpIT extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransferRepository transfers;

    private User alice;
    private BankAccount aliceAccount;
    private String aliceToken;

    @BeforeEach
    void openAccounts() {
        alice = data.customer("alice");
        aliceAccount = data.fundedAccount(alice, "100.00");
        aliceToken = api.loginAsTestUser("alice");
    }

    @Test
    void aTopUpCreditsTheAccountFromTheSystemAccount() {
        var result = topUp(aliceAccount, aliceToken, "250.00", newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var body = api.parse(result);
        assertThat(body.get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.get("description").asText()).isEqualTo("Simulated top-up");
        assertThat(body.get("reference").asText()).isNotBlank();
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("350.00");
        assertThat(databaseCleaner.balanceOf("MB-SYSTEM-EUR"))
                .as("the bank's own position moves the other way")
                .isEqualByComparingTo("-350.00");
    }

    @Test
    void theTopUpAppearsInTheAccountHistory() {
        topUp(aliceAccount, aliceToken, "40.00", newKey());

        var page = api.parse(
                api.get("/api/v1/accounts/" + aliceAccount.getId() + "/transactions", aliceToken));

        assertThat(page.get("content").findValuesAsText("description")).contains("Simulated top-up");
    }

    @Test
    void aTopUpIsAudited() {
        topUp(aliceAccount, aliceToken, "10.00", newKey());

        Integer audited = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'TOP_UP_COMPLETED' AND username = 'alice'",
                Integer.class);
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void replayingTheSameKeyDoesNotCreditTwice() {
        var first = api.parse(topUp(aliceAccount, aliceToken, "50.00", key("top-up-replay-1")));
        var replay = topUp(aliceAccount, aliceToken, "50.00", key("top-up-replay-1"));

        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");
        assertThat(api.parse(replay).get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("150.00");
    }

    @Test
    void reusingAKeyForADifferentAmountIsRefused() {
        topUp(aliceAccount, aliceToken, "50.00", key("top-up-reuse-1"));
        var reused = topUp(aliceAccount, aliceToken, "60.00", key("top-up-reuse-1"));

        assertThat(reused.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(reused).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("150.00");
    }

    @Test
    void anIdempotencyKeyIsRequired() {
        var result = api.post(
                "/api/v1/accounts/" + aliceAccount.getId() + "/top-up", aliceToken, Map.of("amount", "10.00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    void aTopUpOverThePerTopUpCapIsRefused() {
        var result = topUp(aliceAccount, aliceToken, "1000.01", newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("TOP_UP_LIMIT_EXCEEDED");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    void topUpsAreCappedPerDay() {
        assertThat(topUp(aliceAccount, aliceToken, "1000.00", newKey()).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(topUp(aliceAccount, aliceToken, "1000.00", newKey()).getResponse().getStatus())
                .isEqualTo(201);

        var third = topUp(aliceAccount, aliceToken, "0.01", newKey());

        assertThat(third.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(third).get("code").asText()).isEqualTo("TOP_UP_LIMIT_EXCEEDED");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("2100.00");
    }

    @Test
    void anAdminDepositDoesNotCountTowardsTheCustomersDailyCap() {
        // fundedAccount already deposited 100.00 through the operator; a full 2000.00 still fits.
        assertThat(topUp(aliceAccount, aliceToken, "1000.00", newKey()).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(topUp(aliceAccount, aliceToken, "1000.00", newKey()).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void anotherCustomersAccountCannotBeToppedUp() {
        User bob = data.customer("bob");
        BankAccount bobAccount = data.fundedAccount(bob, "0.00");

        var result = topUp(bobAccount, aliceToken, "10.00", newKey());

        assertThat(result.getResponse().getStatus())
                .as("the account is reported missing rather than forbidden, so its existence is not confirmed")
                .isEqualTo(404);
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("0.00");
    }

    @Test
    void anAdministratorCannotTopUpACustomersAccount() {
        data.admin("root");
        String adminToken = api.loginAsTestUser("root");

        var result = topUp(aliceAccount, adminToken, "10.00", newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    void aSavingsAccountCannotBeToppedUp() {
        BankAccount savings = accountService.openAccount(alice, CurrencyCode.EUR, AccountPurpose.SAVINGS);

        var result = topUp(savings, aliceToken, "10.00", newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("TOP_UP_NOT_ALLOWED");
    }

    @Test
    void aFrozenAccountCannotBeToppedUp() {
        data.admin("root");
        api.patch("/api/v1/admin/accounts/" + aliceAccount.getId() + "/freeze", api.loginAsTestUser("root"), null);

        var result = topUp(aliceAccount, aliceToken, "10.00", newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("ACCOUNT_FROZEN");
    }

    @Test
    void anAmountWithTooManyDecimalsIsRejected() {
        var result = api.post(
                "/api/v1/accounts/" + aliceAccount.getId() + "/top-up",
                aliceToken,
                Map.of("amount", new BigDecimal("10.001")),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void aTopUpDoesNotUseUpTheCustomersSendingLimits() {
        topUp(aliceAccount, aliceToken, "500.00", newKey());
        Instant dayAgo = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThat(transfers.sumAmountByInitiatorSince(alice.getId(), dayAgo, List.of(TransferStatus.values())))
                .isEqualByComparingTo("0");
        assertThat(transfers.countByInitiatedByIdAndCreatedAtAfter(alice.getId(), dayAgo)).isZero();
        assertThat(transfers.averageCompletedAmountSince(alice.getId(), dayAgo)).isNull();
    }

    private MvcResult topUp(BankAccount account, String token, String amount, Map<String, String> headers) {
        return api.post(
                "/api/v1/accounts/" + account.getId() + "/top-up",
                token,
                Map.of("amount", new BigDecimal(amount)),
                headers);
    }
}
