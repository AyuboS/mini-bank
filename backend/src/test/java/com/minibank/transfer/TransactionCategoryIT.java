package com.minibank.transfer;

import static com.minibank.support.TransferRequests.newKey;
import static com.minibank.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Recategorising a transaction")
class TransactionCategoryIT extends AbstractIntegrationTest {

    private String aliceToken;
    private String bobToken;
    private String transferId;

    @BeforeEach
    void makeATransfer() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        BankAccount aliceAccount = data.fundedAccount(alice, "100.00");
        BankAccount bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
        bobToken = api.loginAsTestUser("bob");

        JsonNode created = api.parse(api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "40.00"),
                newKey()));
        transferId = created.get("id").asText();
    }

    private String categoryPath() {
        return "/api/v1/transfers/" + transferId + "/category";
    }

    private String categorySeenBy(String token) {
        JsonNode page = api.getAsUser("/api/v1/transactions", token);
        for (JsonNode row : page.get("content")) {
            if (row.get("transferId").asText().equals(transferId)) {
                return row.path("category").asText(null);
            }
        }
        throw new AssertionError("Transfer " + transferId + " is missing from the history");
    }

    @Test
    void aParticipantChangesTheCategoryAndBothPartiesSeeIt() {
        var result = api.patch(categoryPath(), aliceToken, Map.of("category", "GROCERIES"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(result).get("category").asText()).isEqualTo("GROCERIES");
        assertThat(categorySeenBy(aliceToken)).isEqualTo("GROCERIES");
        assertThat(categorySeenBy(bobToken)).isEqualTo("GROCERIES");
    }

    @Test
    void theRecipientMayAlsoChangeIt() {
        var result = api.patch(categoryPath(), bobToken, Map.of("category", "DINING"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(categorySeenBy(aliceToken)).isEqualTo("DINING");
    }

    @Test
    void someoneWhoTookNoPartGetsNotFoundAndNothingChanges() {
        String before = categorySeenBy(aliceToken);
        data.customer("carol");
        String carolToken = api.loginAsTestUser("carol");

        var result = api.patch(categoryPath(), carolToken, Map.of("category", "GROCERIES"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(categorySeenBy(aliceToken)).isEqualTo(before);
    }

    @Test
    void anUnknownCategoryIsRejected() {
        var result = api.patch(categoryPath(), aliceToken, Map.of("category", "NOT_A_CATEGORY"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void aSignedOutCallerIsRejected() {
        var result = api.patch(categoryPath(), null, Map.of("category", "GROCERIES"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void theOldPathWithoutTransfersNoLongerExists() {
        var result = api.patch("/api/v1/" + transferId + "/category", aliceToken, Map.of("category", "GROCERIES"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
