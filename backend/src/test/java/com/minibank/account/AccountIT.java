package com.minibank.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.money.CurrencyCode;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Accounts")
class AccountIT extends AbstractIntegrationTest {

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
        aliceAccount = data.fundedAccount(alice, "250.00");
        bobAccount = data.fundedAccount(bob, "10.00");
        aliceToken = api.loginAsTestUser("alice");
        adminToken = api.loginAsTestUser("root");
    }

    @Test
    void aCustomerSeesOnlyTheirOwnAccounts() {
        var accounts = api.getAsUser("/api/v1/accounts", aliceToken);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).get("accountNumber").asText()).isEqualTo(aliceAccount.getAccountNumber());
    }

    @Test
    void anAccountCanBeReadByItsOwner() {
        var result = api.get("/api/v1/accounts/" + aliceAccount.getId(), aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(result).get("balance").decimalValue()).isEqualByComparingTo("250.00");
    }

    @Test
    void anotherCustomersAccountIsReportedAsNotFoundRatherThanForbidden() {
        // Answering 403 would confirm the id exists, which is itself a disclosure.
        var result = api.get("/api/v1/accounts/" + bobAccount.getId(), aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(api.parse(result).get("code").asText()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void anotherCustomersBalanceIsNotReadable() {
        var result = api.get("/api/v1/accounts/" + bobAccount.getId() + "/balance", aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void anotherCustomersTransactionsAreNotReadable() {
        var result = api.get("/api/v1/accounts/" + bobAccount.getId() + "/transactions", aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void aPayeeLookupReturnsTheHolderAsFirstNameAndLastInitial() {
        var result = api.get("/api/v1/accounts/lookup?accountNumber=" + bobAccount.getAccountNumber(), aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = api.parse(result);
        assertThat(body.get("accountNumber").asText()).isEqualTo(bobAccount.getAccountNumber());
        assertThat(body.get("holderName").asText()).matches("\\S+ \\S\\.");
        assertThat(body.get("bank").asText()).isEqualTo("MBank");
    }

    @Test
    void aPayeeLookupForAnUnknownAccountNumberIsNotFound() {
        var result = api.get("/api/v1/accounts/lookup?accountNumber=MB-000000000000", aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void anUnknownAccountIdIsNotFound() {
        var result = api.get("/api/v1/accounts/" + UUID.randomUUID(), aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void aMalformedAccountIdIsRejectedAsABadRequest() {
        var result = api.get("/api/v1/accounts/not-a-uuid", aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void anAdministratorCanReadAnyAccount() {
        var result = api.get("/api/v1/accounts/" + bobAccount.getId(), adminToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aCustomerCanOpenAnAdditionalAccountInAnotherCurrency() {
        var result = api.post("/api/v1/accounts", aliceToken, Map.of("currency", "USD"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var body = api.parse(result);
        assertThat(body.get("currency").asText()).isEqualTo("USD");
        assertThat(body.get("balance").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.get("accountNumber").asText()).matches("^MB-\\d{12}$");
        assertThat(api.getAsUser("/api/v1/accounts", aliceToken)).hasSize(2);
    }

    @Test
    void anUnsupportedCurrencyIsRejected() {
        var result = api.post("/api/v1/accounts", aliceToken, Map.of("currency", "XYZ"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void accountNumbersAreNotSequential() {
        var first = api.parse(api.post("/api/v1/accounts", aliceToken, Map.of("currency", "USD")))
                .get("accountNumber")
                .asText();
        var second = api.parse(api.post("/api/v1/accounts", aliceToken, Map.of("currency", "GBP")))
                .get("accountNumber")
                .asText();

        long firstDigits = Long.parseLong(first.substring(3));
        long secondDigits = Long.parseLong(second.substring(3));
        assertThat(Math.abs(secondDigits - firstDigits))
                .as("consecutively opened accounts are not adjacent numbers")
                .isGreaterThan(1000L);
    }

    @Test
    void aBalanceIsReportedWithTheCurrencyAndATimestamp() {
        var body = api.parse(api.get("/api/v1/accounts/" + aliceAccount.getId() + "/balance", aliceToken));

        assertThat(body.get("currency").asText()).isEqualTo(CurrencyCode.EUR.name());
        assertThat(body.get("asOf").asText()).isNotEmpty();
        assertThat(body.get("balance").decimalValue()).isEqualByComparingTo("250.00");
    }

    @Test
    void systemAccountsAreNotVisibleToCustomers() {
        var accounts = api.getAsUser("/api/v1/accounts", aliceToken);

        assertThat(accounts).allSatisfy(
                account -> assertThat(account.get("accountNumber").asText()).doesNotContain("SYSTEM"));
    }

    @Test
    void theProfileEndpointReturnsTheCallersOwnUser() {
        var body = api.parse(api.get("/api/v1/users/me", aliceToken));

        assertThat(body.get("username").asText()).isEqualTo("alice");
        assertThat(body.has("passwordHash")).isFalse();
    }

    @Test
    void aProfileUpdateChangesOnlyTheSelfServiceFields() {
        api.patch("/api/v1/users/me", aliceToken, Map.of("firstName", "Alicia", "phoneNumber", "+49 151 12345678"));

        var body = api.parse(api.get("/api/v1/users/me", aliceToken));
        assertThat(body.get("firstName").asText()).isEqualTo("Alicia");
        // Stored in E.164, not as typed: the number is a login credential now, and the same number
        // written three ways has to be one number rather than three accounts.
        assertThat(body.get("phoneNumber").asText()).isEqualTo("+4915112345678");
        // Typing a number does not make it yours; only an SMS code does.
        assertThat(body.get("phoneVerified").asBoolean()).isFalse();
        assertThat(body.get("role").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void aProfileUpdateCannotEscalateTheCallersRole() {
        var result = api.patch("/api/v1/users/me", aliceToken, Map.of("firstName", "Alicia", "role", "ADMIN"));

        assertThat(result.getResponse().getStatus()).as("unknown properties are rejected").isEqualTo(400);
        assertThat(api.parse(api.get("/api/v1/users/me", aliceToken)).get("role").asText())
                .isEqualTo("CUSTOMER");
    }

    @Test
    void aProfileUpdateCannotChangeTheCallersStatus() {
        var result = api.patch("/api/v1/users/me", aliceToken, Map.of("status", "ACTIVE"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void anInvalidPhoneNumberIsRejected() {
        var result = api.patch("/api/v1/users/me", aliceToken, Map.of("phoneNumber", "not a phone number"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.parse(result).get("errors").get(0).get("field").asText()).isEqualTo("phoneNumber");
    }
}
