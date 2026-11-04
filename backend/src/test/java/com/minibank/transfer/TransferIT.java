package com.minibank.transfer;

import static com.minibank.support.TransferRequests.newKey;
import static com.minibank.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.AccountStatus;
import com.minibank.account.domain.BankAccount;
import com.minibank.account.repository.BankAccountRepository;
import com.minibank.common.money.CurrencyCode;
import com.minibank.ledger.service.LedgerService;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Money transfer")
class TransferIT extends AbstractIntegrationTest {

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerService ledgerService;

    private User alice;
    private User bob;
    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;

    @BeforeEach
    void openAccounts() {
        alice = data.customer("alice");
        bob = data.customer("bob");
        aliceAccount = data.fundedAccount(alice, "100.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
    }

    @Test
    @DisplayName("100 EUR, transfer 40, leaves 60 and 40")
    void aSuccessfulTransferMovesExactlyTheRequestedAmount() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "40.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var body = api.parse(result);
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.get("reference").asText()).matches("^TRX-\\d{4}-[A-Z2-9]{8}$");

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("60.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("40.00");
    }

    @Test
    void aTransferWritesExactlyOneBalancedPairOfLedgerEntries() {
        var body = api.parse(api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "40.00"),
                newKey()));
        UUID transferId = UUID.fromString(body.get("id").asText());

        var entries = api.parse(api.get("/api/v1/transfers/" + transferId + "/ledger", aliceToken));

        assertThat(entries).hasSize(2);
        assertThat(ledgerService.isTransferBalanced(transferId))
                .as("debits and credits cancel out")
                .isTrue();

        var debit = entries.get(0).get("entryType").asText().equals("DEBIT") ? entries.get(0) : entries.get(1);
        var credit = entries.get(0).get("entryType").asText().equals("CREDIT") ? entries.get(0) : entries.get(1);
        assertThat(debit.get("accountNumber").asText()).isEqualTo(aliceAccount.getAccountNumber());
        assertThat(debit.get("balanceAfter").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(credit.get("accountNumber").asText()).isEqualTo(bobAccount.getAccountNumber());
        assertThat(credit.get("balanceAfter").decimalValue()).isEqualByComparingTo("40.00");
    }

    @Test
    void everyAccountBalanceStillEqualsItsLedgerPosition() {
        api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "40.00"),
                newKey());

        for (BankAccount account : bankAccountRepository.findAll()) {
            assertThat(ledgerService.computeBalanceFromLedger(account.getId()))
                    .as("cached balance of %s matches its ledger", account.getAccountNumber())
                    .isEqualByComparingTo(account.getBalance());
        }
    }

    @Test
    void aTransferOfTheEntireBalanceIsAllowed() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("0");
    }

    @Test
    void aTransferBeyondTheBalanceIsRefusedAndNothingMoves() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "100.01"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("0");
    }

    @Test
    void aRefusedTransferLeavesNoLedgerEntriesButIsStillRecorded() {
        api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "500.00"),
                newKey());

        var failed = transferRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransferStatus.FAILED)
                .toList();

        assertThat(failed).as("the attempt is visible in history").hasSize(1);
        assertThat(failed.get(0).getFailureReason()).contains("sufficient funds");
        assertThat(ledgerService.computeBalanceFromLedger(aliceAccount.getId()))
                .as("no postings were made for the failed transfer")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void aNegativeAmountIsRejectedByValidation() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "-40.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void aZeroAmountIsRejected() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "0.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void anOverlyPreciseAmountIsRejectedRatherThanRounded() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.005"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void aTransferToTheSameAccountIsRejected() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), aliceAccount.getAccountNumber(), "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("SAME_ACCOUNT_TRANSFER");
    }

    @Test
    void aTransferToAnAccountThatDoesNotExistIsRejected() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), "MB-000000000000", "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void aTransferFromAFrozenAccountIsRejected() {
        freeze(aliceAccount.getId());

        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("ACCOUNT_FROZEN");
    }

    @Test
    void aTransferToAFrozenAccountIsRejected() {
        freeze(bobAccount.getId());

        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("ACCOUNT_FROZEN");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    void aTransferBetweenDifferentCurrenciesIsRejectedRatherThanConverted() {
        BankAccount bobDollars = data.account(bob, CurrencyCode.USD);

        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobDollars.getAccountNumber(), "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void aCurrencyThatDisagreesWithTheSourceAccountIsRejected() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00", "USD"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void aCustomerCannotSpendFromSomebodyElsesAccount() {
        String bobToken = api.loginAsTestUser("bob");

        var result = api.post(
                "/api/v1/transfers",
                bobToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    void theIdempotencyKeyHeaderIsRequired() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void bothPartiesCanSeeTheTransferInTheirHistory() {
        api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "40.00"),
                newKey());

        var aliceHistory = api.parse(api.get("/api/v1/transactions", aliceToken));
        var bobHistory = api.parse(api.get("/api/v1/transactions", api.loginAsTestUser("bob")));

        var aliceOutgoing = aliceHistory.get("content").get(0);
        assertThat(aliceOutgoing.get("direction").asText()).isEqualTo("DEBIT");
        assertThat(aliceOutgoing.get("counterpartyAccountNumber").asText()).isEqualTo(bobAccount.getAccountNumber());

        var bobIncoming = bobHistory.get("content").get(0);
        assertThat(bobIncoming.get("direction").asText()).isEqualTo("CREDIT");
        assertThat(bobIncoming.get("counterpartyAccountNumber").asText()).isEqualTo(aliceAccount.getAccountNumber());
    }

    @Test
    void aCustomerCannotReadATransferTheyWereNotPartyTo() {
        User carol = data.customer("carol");
        data.fundedAccount(carol, "10.00");
        var body = api.parse(api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "40.00"),
                newKey()));

        var result = api.get("/api/v1/transfers/" + body.get("id").asText(), api.loginAsTestUser("carol"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void transactionHistoryIsPagedInTheDatabase() {
        for (int i = 0; i < 5; i++) {
            api.post(
                    "/api/v1/transfers",
                    aliceToken,
                    transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "1.00"),
                    newKey());
        }

        var page = api.parse(api.get("/api/v1/transactions?page=0&size=2&sort=createdAt,desc", aliceToken));

        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("size").asInt()).isEqualTo(2);
        assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(page.get("first").asBoolean()).isTrue();
    }

    /** Freezes an account by re-reading it, so a stale version from setup cannot break the save. */
    private void freeze(UUID accountId) {
        BankAccount current = bankAccountRepository.findById(accountId).orElseThrow();
        current.setStatus(AccountStatus.FROZEN);
        bankAccountRepository.save(current);
    }

    @Test
    void anUnknownSortPropertyIsIgnoredRatherThanFailing() {
        var result = api.get("/api/v1/transactions?sort=passwordHash,desc", aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
}
