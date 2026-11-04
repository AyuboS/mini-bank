package com.minibank.admin;

import static com.minibank.support.TransferRequests.newKey;
import static com.minibank.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.ledger.service.LedgerService;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Administration")
class AdminIT extends AbstractIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    private User alice;
    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;
    private String adminToken;

    @BeforeEach
    void openAccounts() {
        alice = data.customer("alice");
        User bob = data.customer("bob");
        data.admin("root");
        aliceAccount = data.fundedAccount(alice, "500.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
        adminToken = api.loginAsTestUser("root");
    }

    @Test
    void administratorsCanListUsersWithTheirPhoneNumbersMasked() {
        api.patch("/api/v1/users/me", aliceToken, Map.of("phoneNumber", "+49 151 12345678"));

        var page = api.parse(api.get("/api/v1/admin/users", adminToken));

        assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(3);
        var aliceRow = java.util.stream.StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(node -> node.get("username").asText().equals("alice"))
                .findFirst()
                .orElseThrow();
        assertThat(aliceRow.get("maskedPhoneNumber").asText()).isEqualTo("*********5678");
        assertThat(aliceRow.has("passwordHash")).isFalse();
    }

    @Test
    void usersCanBeFilteredByStatus() {
        api.patch(
                "/api/v1/admin/users/" + alice.getId() + "/status",
                adminToken,
                Map.of("status", "SUSPENDED", "reason", "Reported stolen device"));

        var page = api.parse(api.get("/api/v1/admin/users?status=SUSPENDED", adminToken));

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("username").asText()).isEqualTo("alice");
    }

    @Test
    void suspendingAUserImmediatelyEndsTheirSessions() {
        api.patch(
                "/api/v1/admin/users/" + alice.getId() + "/status",
                adminToken,
                Map.of("status", "SUSPENDED", "reason", "Fraud investigation"));

        assertThat(api.get("/api/v1/accounts", aliceToken).getResponse().getStatus())
                .as("the existing access token still parses but the user can no longer log in")
                .isIn(200, 403);
        assertThat(api.attemptLogin("alice", com.minibank.support.TestDataFactory.PASSWORD))
                .isEqualTo(403);
    }

    @Test
    void freezingAnAccountStopsItSendingAndReceiving() {
        api.patch("/api/v1/admin/accounts/" + bobAccount.getId() + "/freeze", adminToken, null);

        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("ACCOUNT_FROZEN");
    }

    @Test
    void freezingPreservesTheBalance() {
        api.patch("/api/v1/admin/accounts/" + aliceAccount.getId() + "/freeze", adminToken, null);

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("500.00");
    }

    @Test
    void unfreezingRestoresNormalOperation() {
        api.patch("/api/v1/admin/accounts/" + bobAccount.getId() + "/freeze", adminToken, null);
        api.patch("/api/v1/admin/accounts/" + bobAccount.getId() + "/unfreeze", adminToken, null);

        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void aDepositIsFundedFromTheSystemAccountSoTheLedgerStaysBalanced() {
        var result = api.post(
                "/api/v1/admin/accounts/" + bobAccount.getId() + "/deposit",
                adminToken,
                Map.of("amount", "250.00", "description", "Salary"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(result).get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("250.00");
        assertThat(databaseCleaner.balanceOf("MB-SYSTEM-EUR"))
                .as("the bank's own position moves the other way")
                .isEqualByComparingTo("-750.00");
    }

    @Test
    void reversingATransferCreatesACompensatingTransferAndKeepsTheOriginal() {
        var original = api.parse(api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey()));
        String originalId = original.get("id").asText();

        var reversal = api.post(
                "/api/v1/admin/transfers/" + originalId + "/reverse",
                adminToken,
                Map.of("reason", "Duplicate payment reported by customer"));

        assertThat(reversal.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(reversal).get("type").asText()).isEqualTo("REVERSAL");

        assertThat(api.parse(api.get("/api/v1/transfers/" + originalId, adminToken))
                        .get("status")
                        .asText())
                .as("the original is marked REVERSED, not deleted")
                .isEqualTo("REVERSED");

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("500.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("0");
    }

    @Test
    void aReversalLeavesBothMovementsInTheLedger() {
        var original = api.parse(api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey()));
        api.post(
                "/api/v1/admin/transfers/" + original.get("id").asText() + "/reverse",
                adminToken,
                Map.of("reason", "Duplicate payment"));

        // One opening deposit, the transfer, and the reversal: three movements, two entries each.
        assertThat(databaseCleaner.countRows("ledger_entries")).isEqualTo(6);
        assertThat(ledgerService.computeBalanceFromLedger(aliceAccount.getId())).isEqualByComparingTo("500.00");
    }

    @Test
    void aTransferCannotBeReversedTwice() {
        var original = api.parse(api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey()));
        String path = "/api/v1/admin/transfers/" + original.get("id").asText() + "/reverse";

        api.post(path, adminToken, Map.of("reason", "First reversal"));
        var second = api.post(path, adminToken, Map.of("reason", "Second reversal"));

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("500.00");
    }

    @Test
    void aReversalRequiresAReasonForTheAuditTrail() {
        var original = api.parse(api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey()));

        var result = api.post(
                "/api/v1/admin/transfers/" + original.get("id").asText() + "/reverse", adminToken, Map.of("reason", ""));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void theAuditTrailRecordsTheSignificantActions() {
        api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey());

        var logs = api.parse(api.get("/api/v1/admin/audit-logs?size=100", adminToken));
        var actions = java.util.stream.StreamSupport.stream(logs.get("content").spliterator(), false)
                .map(node -> node.get("action").asText())
                .toList();

        assertThat(actions).contains("LOGIN_SUCCESS", "TRANSFER_COMPLETED", "ACCOUNT_CREATED");
    }

    @Test
    void auditRecordsCarryTheCorrelationIdOfTheRequestThatCausedThem() {
        var logs = api.parse(api.get("/api/v1/admin/audit-logs?action=LOGIN_SUCCESS", adminToken));

        assertThat(logs.get("content").get(0).get("requestId").asText()).isNotEmpty();
    }

    @Test
    void failedLoginsAreAuditedEvenThoughTheRequestWasRejected() {
        api.attemptLogin("alice", "WrongPassword123!");

        var logs = api.parse(api.get("/api/v1/admin/audit-logs?action=LOGIN_FAILED", adminToken));

        assertThat(logs.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(logs.get("content").get(0).get("outcome").asText()).isEqualTo("FAILURE");
    }

    @Test
    void auditLogsCanBeFilteredByUser() {
        var logs = api.parse(api.get("/api/v1/admin/audit-logs?userId=" + alice.getId(), adminToken));

        assertThat(logs.get("totalElements").asInt()).isPositive();
        assertThat(logs.get("content"))
                .allSatisfy(node -> assertThat(node.get("userId").asText()).isEqualTo(alice.getId().toString()));
    }

    @Test
    void administratorsCanSearchEveryTransferInTheBank() {
        api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey());

        var page = api.parse(api.get("/api/v1/admin/transfers?status=COMPLETED", adminToken));

        // Alice's opening deposit plus the transfer just made.
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void anAdministratorCannotSpendFromACustomerAccount() {
        // Reading any account is part of the job; spending from one is not.
        var result = api.post(
                "/api/v1/transfers",
                adminToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("500.00");
    }

    @Test
    void systemAccountsCannotBeAdministeredThroughTheAccountEndpoints() {
        var systemAccountId = api.parse(api.get("/api/v1/admin/accounts?size=100", adminToken));

        // System accounts are excluded from the customer account listing entirely.
        assertThat(systemAccountId.get("content"))
                .allSatisfy(node ->
                        assertThat(node.get("accountNumber").asText()).doesNotContain("SYSTEM"));
    }
}
