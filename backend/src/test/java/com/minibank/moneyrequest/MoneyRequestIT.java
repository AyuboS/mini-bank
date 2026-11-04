package com.minibank.moneyrequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Payment requests.
 *
 * <p>The security property under test is the one the whole design rests on: a request moves no money
 * by existing, and only the payer can settle it. If a requester could cause a debit, the feature
 * would be a way for one customer to take another's money.
 */
@DisplayName("Money requests")
class MoneyRequestIT extends AbstractIntegrationTest {

    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void signIn() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        aliceAccount = data.fundedAccount(alice, "100.00");
        bobAccount = data.fundedAccount(bob, "500.00");
        aliceToken = api.loginAsTestUser("alice");
        bobToken = api.loginAsTestUser("bob");
    }

    @Test
    @DisplayName("raising a request moves no money")
    void raisingARequestMovesNothing() {
        JsonNode request = raise("25.00");

        assertThat(request.get("status").asText()).isEqualTo("PENDING");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("the payer settles it with a real transfer")
    void thePayerSettlesIt() {
        String id = raise("25.00").get("id").asText();

        JsonNode paid = api.parse(api.post(
                "/api/v1/money-requests/" + id + "/pay", bobToken, Map.of("fromAccountId", bobAccount.getId())));

        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("settledTransferId").asText()).isNotBlank();
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("475.00");
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("the requester cannot pay their own request")
    void theRequesterCannotPayItThemselves() {
        String id = raise("25.00").get("id").asText();

        // Alice raised it; the request names Bob's account, so her own account is not the one it
        // was addressed to. This is the check that stops a request from becoming a pull.
        var response = api.post(
                "/api/v1/money-requests/" + id + "/pay", aliceToken, Map.of("fromAccountId", aliceAccount.getId()));

        assertThat(response.getResponse().getStatus()).isEqualTo(403);
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("paying twice does not move the money twice")
    void payingTwiceIsIdempotent() {
        String id = raise("25.00").get("id").asText();

        api.post("/api/v1/money-requests/" + id + "/pay", bobToken, Map.of("fromAccountId", bobAccount.getId()));
        var second = api.post(
                "/api/v1/money-requests/" + id + "/pay", bobToken, Map.of("fromAccountId", bobAccount.getId()));

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("475.00");
    }

    @Test
    @DisplayName("the payer can decline, and the requester is told")
    void thePayerCanDecline() {
        String id = raise("25.00").get("id").asText();

        JsonNode declined = api.parse(api.patch("/api/v1/money-requests/" + id + "/decline", bobToken, null));
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("500.00");

        JsonNode feed = api.getAsUser("/api/v1/notifications", aliceToken).get("content");
        assertThat(feed.findValuesAsText("title")).contains("Your request was declined");
    }

    @Test
    @DisplayName("only the requester can withdraw a request")
    void onlyTheRequesterCanCancel() {
        String id = raise("25.00").get("id").asText();

        // The payer trying to cancel gets a 404: the endpoint is not theirs to use, and saying
        // "forbidden" would confirm the id.
        assertThat(api.patch("/api/v1/money-requests/" + id + "/cancel", bobToken, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);

        assertThat(api.parse(api.patch("/api/v1/money-requests/" + id + "/cancel", aliceToken, null))
                        .get("status")
                        .asText())
                .isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("each side sees the request from their own direction")
    void bothSidesSeeIt() {
        raise("25.00");

        JsonNode outgoing = api.getAsUser("/api/v1/money-requests/outgoing", aliceToken);
        assertThat(outgoing).hasSize(1);
        assertThat(outgoing.get(0).get("direction").asText()).isEqualTo("OUTGOING");

        JsonNode incoming = api.getAsUser("/api/v1/money-requests/incoming", bobToken);
        assertThat(incoming).hasSize(1);
        assertThat(incoming.get(0).get("direction").asText()).isEqualTo("INCOMING");
        assertThat(incoming.get(0).get("requesterName").asText()).isNotBlank();
    }

    @Test
    @DisplayName("an uninvolved customer sees nothing")
    void anUninvolvedCustomerSeesNothing() {
        raise("25.00");
        User mallory = data.customer("mallory");
        data.fundedAccount(mallory, "0.00");
        String malloryToken = api.loginAsTestUser("mallory");

        assertThat(api.getAsUser("/api/v1/money-requests/incoming", malloryToken)).isEmpty();
        assertThat(api.getAsUser("/api/v1/money-requests/outgoing", malloryToken)).isEmpty();
    }

    /** Alice asks Bob. */
    private JsonNode raise(String amount) {
        var body = new HashMap<String, Object>();
        body.put("toAccountId", aliceAccount.getId());
        body.put("fromAccountNumber", bobAccount.getAccountNumber());
        body.put("amount", new BigDecimal(amount));
        body.put("description", "Dinner");
        var result = api.post("/api/v1/money-requests", aliceToken, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return api.parse(result);
    }
}
