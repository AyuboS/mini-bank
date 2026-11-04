package com.minibank.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Beneficiaries")
class BeneficiaryIT extends AbstractIntegrationTest {

    private BankAccount bobAccount;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void openAccounts() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        data.fundedAccount(alice, "100.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
        bobToken = api.loginAsTestUser("bob");
    }

    private Map<String, String> payee(String accountNumber, String nickname) {
        return Map.of("accountNumber", accountNumber, "nickname", nickname);
    }

    @Test
    void aPayeeCanBeSavedAndListed() {
        var created = api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord"));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(api.parse(created).get("nickname").asText()).isEqualTo("Landlord");

        var list = api.getAsUser("/api/v1/beneficiaries", aliceToken);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("accountNumber").asText()).isEqualTo(bobAccount.getAccountNumber());
    }

    @Test
    void onlyAnAbbreviatedHolderNameIsDisclosed() {
        // Enough to confirm the payer is paying the right person, without turning the endpoint into
        // a directory that maps account numbers to full identities.
        var created = api.parse(
                api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord")));

        String holder = created.get("holderName").asText();
        assertThat(holder).startsWith("Test").matches("^Test [A-Z]\\.$");
    }

    @Test
    void savingAnAccountThatDoesNotExistIsRejected() {
        var result = api.post("/api/v1/beneficiaries", aliceToken, payee("MB-000000000000", "Nobody"));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void aMalformedAccountNumberIsRejectedByValidation() {
        var result = api.post("/api/v1/beneficiaries", aliceToken, payee("12345", "Nobody"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.parse(result).get("errors").get(0).get("field").asText()).isEqualTo("accountNumber");
    }

    @Test
    void theSamePayeeCannotBeSavedTwice() {
        api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord"));

        var result = api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Again"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("BENEFICIARY_ALREADY_EXISTS");
    }

    @Test
    void aPayeeCanBeRemovedAndReAddedAfterwards() {
        var created = api.parse(
                api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord")));

        var deleted = api.delete("/api/v1/beneficiaries/" + created.get("id").asText(), aliceToken);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
        assertThat(api.getAsUser("/api/v1/beneficiaries", aliceToken)).isEmpty();

        // The partial unique index is scoped to ACTIVE rows, so re-adding is possible.
        var recreated = api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord"));
        assertThat(recreated.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void deletionIsSoftSoTheRelationshipHistoryIsKept() {
        var created = api.parse(
                api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord")));

        api.delete("/api/v1/beneficiaries/" + created.get("id").asText(), aliceToken);

        assertThat(databaseCleaner.countRows("beneficiaries"))
                .as("the row survives for audit and risk purposes")
                .isEqualTo(1);
    }

    @Test
    void oneCustomerCannotSeeAnothersPayees() {
        api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord"));

        assertThat(api.getAsUser("/api/v1/beneficiaries", bobToken)).isEmpty();
    }

    @Test
    void oneCustomerCannotDeleteAnothersPayee() {
        var created = api.parse(
                api.post("/api/v1/beneficiaries", aliceToken, payee(bobAccount.getAccountNumber(), "Landlord")));

        var result = api.delete("/api/v1/beneficiaries/" + created.get("id").asText(), bobToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(api.getAsUser("/api/v1/beneficiaries", aliceToken)).hasSize(1);
    }

    @Test
    void deletingAnUnknownPayeeIsNotFound() {
        var result = api.delete("/api/v1/beneficiaries/" + UUID.randomUUID(), aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void beneficiariesRequireAuthentication() {
        var result = api.get("/api/v1/beneficiaries", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
