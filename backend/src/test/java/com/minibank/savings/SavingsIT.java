package com.minibank.savings;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Savings goals.
 *
 * <p>The assertions worth reading are the ones about money: a contribution must leave the funding
 * account, arrive in the goal's own account, and show up in the ledger. A savings feature that only
 * increments a counter would pass a weaker test and be wrong in production.
 */
@DisplayName("Savings goals")
class SavingsIT extends AbstractIntegrationTest {

    private User alice;
    private BankAccount aliceAccount;
    private String aliceToken;

    @BeforeEach
    void signIn() {
        alice = data.customer("alice");
        aliceAccount = data.fundedAccount(alice, "1000.00");
        aliceToken = api.loginAsTestUser("alice");
    }

    @Test
    @DisplayName("opens a goal backed by a real account of its own")
    void opensAGoal() {
        JsonNode goal = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Thailand trip", "1500.00")));

        assertThat(goal.get("name").asText()).isEqualTo("Thailand trip");
        assertThat(goal.get("progressPercent").asInt()).isZero();
        assertThat(goal.get("saved").decimalValue()).isEqualByComparingTo("0");

        // The backing account is real: it has a number, and it appears in the customer's accounts.
        String goalAccountNumber = goal.get("accountNumber").asText();
        assertThat(goalAccountNumber).matches("^MB-[0-9]{12}$");

        JsonNode accounts = api.getAsUser("/api/v1/accounts", aliceToken);
        assertThat(accounts.findValuesAsText("accountNumber")).contains(goalAccountNumber);
        assertThat(accounts.findValuesAsText("purpose")).contains("SAVINGS");
    }

    @Test
    @DisplayName("a contribution actually moves money through the ledger")
    void contributionMovesMoney() {
        JsonNode goal = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Thailand trip", "1500.00")));
        String goalId = goal.get("id").asText();
        String goalAccountNumber = goal.get("accountNumber").asText();

        JsonNode after = api.parse(api.post(
                "/api/v1/savings/goals/" + goalId + "/contributions",
                aliceToken,
                Map.of("accountId", aliceAccount.getId(), "amount", "650.00")));

        assertThat(after.get("saved").decimalValue()).isEqualByComparingTo("650.00");
        assertThat(after.get("remaining").decimalValue()).isEqualByComparingTo("850.00");
        // 650/1500 is 43.33%, and progress rounds down so a goal never overstates itself.
        assertThat(after.get("progressPercent").asInt()).isEqualTo(43);

        // The money left one account and arrived in the other. Read straight from SQL, so this is
        // the committed balance rather than anything JPA is holding.
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber()))
                .isEqualByComparingTo("350.00");
        assertThat(databaseCleaner.balanceOf(goalAccountNumber)).isEqualByComparingTo("650.00");
    }

    @Test
    @DisplayName("refuses a contribution the funding account cannot cover")
    void refusesAnUnaffordableContribution() {
        String goalId = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Car", "20000.00")))
                .get("id")
                .asText();

        var response = api.post(
                "/api/v1/savings/goals/" + goalId + "/contributions",
                aliceToken,
                Map.of("accountId", aliceAccount.getId(), "amount", "5000.00"));

        assertThat(response.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(response).get("code").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        // Nothing moved.
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber()))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("reaching the target marks the goal achieved, and taking money back out un-marks it")
    void statusFollowsTheBalanceBothWays() {
        String goalId = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Laptop", "500.00")))
                .get("id")
                .asText();

        JsonNode full = api.parse(api.post(
                "/api/v1/savings/goals/" + goalId + "/contributions",
                aliceToken,
                Map.of("accountId", aliceAccount.getId(), "amount", "500.00")));
        assertThat(full.get("status").asText()).isEqualTo("ACHIEVED");
        assertThat(full.get("progressPercent").asInt()).isEqualTo(100);

        JsonNode afterWithdrawal = api.parse(api.post(
                "/api/v1/savings/goals/" + goalId + "/withdrawals",
                aliceToken,
                Map.of("accountId", aliceAccount.getId(), "amount", "100.00")));
        assertThat(afterWithdrawal.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(afterWithdrawal.get("saved").decimalValue()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("will not archive a goal that still holds money")
    void refusesToArchiveWithABalance() {
        String goalId = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Trip", "900.00")))
                .get("id")
                .asText();
        api.post(
                "/api/v1/savings/goals/" + goalId + "/contributions",
                aliceToken,
                Map.of("accountId", aliceAccount.getId(), "amount", "50.00"));

        var refused = api.patch("/api/v1/savings/goals/" + goalId + "/archive", aliceToken, null);
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(refused).get("code").asText()).isEqualTo("SAVINGS_GOAL_NOT_EMPTY");

        // Empty it, and archiving is allowed.
        api.post(
                "/api/v1/savings/goals/" + goalId + "/withdrawals",
                aliceToken,
                Map.of("accountId", aliceAccount.getId(), "amount", "50.00"));
        assertThat(api.patch("/api/v1/savings/goals/" + goalId + "/archive", aliceToken, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("another customer's goal is reported as missing, not forbidden")
    void goalsAreNotVisibleAcrossCustomers() {
        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");

        String goalId = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Private", "100.00")))
                .get("id")
                .asText();

        // 404 rather than 403: a 403 would confirm the id exists.
        assertThat(api.get("/api/v1/savings/goals/" + goalId, malloryToken)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
        assertThat(api.getAsUser("/api/v1/savings/goals", malloryToken)).isEmpty();
    }

    @Test
    @DisplayName("refuses to fund a EUR goal from a USD account")
    void refusesACurrencyMismatch() {
        BankAccount usd = data.account(alice, com.minibank.common.money.CurrencyCode.USD);
        String goalId = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Trip", "500.00")))
                .get("id")
                .asText();

        var response = api.post(
                "/api/v1/savings/goals/" + goalId + "/contributions",
                aliceToken,
                Map.of("accountId", usd.getId(), "amount", "10.00"));

        assertThat(response.getResponse().getStatus()).isEqualTo(422);
        assertThat(api.parse(response).get("code").asText()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    @DisplayName("a savings contribution is categorised as savings, so it never reads as spending")
    void contributionsAreCategorisedAsSavings() {
        String goalId = api.parse(api.post("/api/v1/savings/goals", aliceToken, goalRequest("Trip", "500.00")))
                .get("id")
                .asText();
        api.post(
                "/api/v1/savings/goals/" + goalId + "/contributions",
                aliceToken,
                Map.of("accountId", aliceAccount.getId(), "amount", "200.00"));

        JsonNode transactions = api.getAsUser("/api/v1/transactions?size=50", aliceToken).get("content");
        assertThat(transactions.findValuesAsText("category")).contains("SAVINGS");
    }

    private static Map<String, Object> goalRequest(String name, String target) {
        return Map.of("name", name, "targetAmount", new BigDecimal(target), "currency", "EUR", "accent", "cyan");
    }
}
