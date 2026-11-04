package com.minibank.transfer;

import static com.minibank.support.TransferRequests.key;
import static com.minibank.support.TransferRequests.newKey;
import static com.minibank.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Idempotency: the guarantee that a client can retry a payment without paying twice.
 *
 * <p>This matters because the failure a client cannot distinguish is "the request never arrived"
 * from "the response never came back". Without a key, the safe action after a timeout is to do
 * nothing, which loses payments; with one, retrying is always safe.
 */
@DisplayName("Transfer idempotency")
class TransferIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private TransferRepository transferRepository;

    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;

    @BeforeEach
    void openAccounts() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        aliceAccount = data.fundedAccount(alice, "500.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
    }

    private Map<String, Object> body(String amount) {
        return transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), amount);
    }

    @Test
    void replayingAKeyReturnsTheOriginalResultAndMovesNoFurtherMoney() {
        var headers = key("retry-me-once");

        var first = api.parse(api.post("/api/v1/transfers", aliceToken, body("100.00"), headers));
        var second = api.post("/api/v1/transfers", aliceToken, body("100.00"), headers);

        assertThat(api.parse(second).get("reference").asText())
                .as("the same transfer is returned, not a new one")
                .isEqualTo(first.get("reference").asText());
        assertThat(api.parse(second).get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(second.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("400.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    void replayingAKeyCreatesExactlyOneTransferAndOnePairOfLedgerEntries() {
        var headers = key("retry-me-many-times");

        for (int i = 0; i < 5; i++) {
            api.post("/api/v1/transfers", aliceToken, body("100.00"), headers);
        }

        long customerTransfers = transferRepository.findAll().stream()
                .filter(t -> t.getTransferType() == com.minibank.transfer.domain.TransferType.INTERNAL)
                .count();
        assertThat(customerTransfers).isEqualTo(1);
        assertThat(databaseCleaner.countRows("ledger_entries"))
                .as("Alice's opening deposit plus exactly one transfer, two entries each")
                .isEqualTo(4);
    }

    @Test
    void aFirstRequestIsNotMarkedAsAReplay() {
        var result = api.post("/api/v1/transfers", aliceToken, body("10.00"), newKey());

        assertThat(result.getResponse().getHeader("Idempotent-Replay")).isEqualTo("false");
    }

    @Test
    void reusingAKeyWithADifferentPayloadIsRejected() {
        var headers = key("do-not-reuse-me");
        api.post("/api/v1/transfers", aliceToken, body("100.00"), headers);

        var result = api.post("/api/v1/transfers", aliceToken, body("250.00"), headers);

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber()))
                .as("the second, different request moved nothing")
                .isEqualByComparingTo("400.00");
    }

    @Test
    void aBusinessFailureIsReplayedAsTheSameFailure() {
        var headers = key("this-one-will-fail");

        var first = api.post("/api/v1/transfers", aliceToken, body("9999.00"), headers);
        var second = api.post("/api/v1/transfers", aliceToken, body("9999.00"), headers);

        assertThat(first.getResponse().getStatus()).isEqualTo(422);
        assertThat(second.getResponse().getStatus())
                .as("the answer does not change just because the client asked twice")
                .isEqualTo(422);
        assertThat(api.parse(second).get("code").asText())
                .isEqualTo(api.parse(first).get("code").asText())
                .isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void keysAreScopedPerUserSoTwoCustomersCannotCollide() {
        User carol = data.customer("carol");
        BankAccount carolAccount = data.fundedAccount(carol, "500.00");
        String carolToken = api.loginAsTestUser("carol");
        var sharedKey = key("a-key-both-clients-happened-to-pick");

        api.post("/api/v1/transfers", aliceToken, body("100.00"), sharedKey);
        var carolResult = api.post(
                "/api/v1/transfers",
                carolToken,
                transfer(carolAccount.getId(), bobAccount.getAccountNumber(), "50.00"),
                sharedKey);

        assertThat(carolResult.getResponse().getStatus())
                .as("Carol's request is unaffected by Alice using the same key")
                .isEqualTo(201);
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("150.00");
    }

    @Test
    void aTooShortKeyIsRejected() {
        var result = api.post("/api/v1/transfers", aliceToken, body("10.00"), key("short"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void distinctKeysProduceDistinctTransfers() {
        api.post("/api/v1/transfers", aliceToken, body("100.00"), newKey());
        api.post("/api/v1/transfers", aliceToken, body("100.00"), newKey());

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("300.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("200.00");
    }
}
