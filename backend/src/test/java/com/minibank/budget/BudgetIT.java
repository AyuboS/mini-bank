package com.minibank.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Monthly budgets, and the progress derived from real transactions. */
@DisplayName("Budgets")
class BudgetIT extends AbstractIntegrationTest {

    private BankAccount aliceAccount;
    private String aliceToken;
    private String payeeAccountNumber;

    @BeforeEach
    void signIn() {
        User alice = data.customer("alice");
        aliceAccount = data.fundedAccount(alice, "2000.00");
        aliceToken = api.loginAsTestUser("alice");

        User bob = data.customer("bob");
        payeeAccountNumber = data.fundedAccount(bob, "0.00").getAccountNumber();
    }

    @Test
    @DisplayName("setting a cap twice moves it rather than creating a second budget")
    void settingIsAnUpsert() {
        api.put("/api/v1/budgets", aliceToken, budget("GROCERIES", "300.00"));
        api.put("/api/v1/budgets", aliceToken, budget("GROCERIES", "250.00"));

        JsonNode budgets = api.getAsUser("/api/v1/budgets?currency=EUR", aliceToken);
        assertThat(budgets).hasSize(1);
        assertThat(budgets.get(0).get("amount").decimalValue()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("spend is computed from transactions, not stored")
    void spendComesFromRealTransactions() {
        api.put("/api/v1/budgets", aliceToken, budget("GROCERIES", "300.00"));
        spend("100.00", "Weekly shop", "GROCERIES");
        spend("80.00", "Market", "GROCERIES");
        // A different category must not count against this budget.
        spend("500.00", "Rent", "BILLS");

        JsonNode budget = api.getAsUser("/api/v1/budgets?currency=EUR", aliceToken).get(0);
        assertThat(budget.get("spent").decimalValue()).isEqualByComparingTo("180.00");
        assertThat(budget.get("remaining").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(budget.get("usedPercent").asInt()).isEqualTo(60);
        assertThat(budget.get("state").asText()).isEqualTo("OK");
    }

    @Test
    @DisplayName("warns at 80% and reports an overspend without inventing a negative allowance")
    void reportsNearingAndOver() {
        api.put("/api/v1/budgets", aliceToken, budget("DINING", "100.00"));

        spend("85.00", "Dinner", "DINING");
        JsonNode nearing = api.getAsUser("/api/v1/budgets?currency=EUR", aliceToken).get(0);
        assertThat(nearing.get("state").asText()).isEqualTo("NEARING");

        spend("60.00", "More dinner", "DINING");
        JsonNode over = api.getAsUser("/api/v1/budgets?currency=EUR", aliceToken).get(0);
        assertThat(over.get("state").asText()).isEqualTo("OVER");
        assertThat(over.get("spent").decimalValue()).isEqualByComparingTo("145.00");
        // Remaining floors at zero: "-€45 remaining" would read as a debt owed to a budget.
        assertThat(over.get("remaining").decimalValue()).isEqualByComparingTo("0");
        // The capped figure stays in range while the raw one shows how far past the cap it went.
        assertThat(over.get("usedPercent").asInt()).isEqualTo(100);
        assertThat(over.get("usedPercentRaw").asInt()).isEqualTo(145);
    }

    @Test
    @DisplayName("passing a budget notifies the customer who set it")
    void overspendRaisesANotification() {
        api.put("/api/v1/budgets", aliceToken, budget("DINING", "50.00"));
        spend("70.00", "Dinner", "DINING");

        JsonNode feed = api.getAsUser("/api/v1/notifications?type=BUDGET", aliceToken).get("content");
        assertThat(feed).isNotEmpty();
        assertThat(feed.get(0).get("title").asText()).contains("Dining");
    }

    @Test
    @DisplayName("budgets are private to the customer who set them")
    void budgetsAreScopedToTheirOwner() {
        api.put("/api/v1/budgets", aliceToken, budget("GROCERIES", "300.00"));

        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");
        assertThat(api.getAsUser("/api/v1/budgets?currency=EUR", malloryToken)).isEmpty();
    }

    private static Map<String, Object> budget(String category, String amount) {
        return Map.of("category", category, "amount", new BigDecimal(amount), "currency", "EUR");
    }

    private void spend(String amount, String description, String category) {
        var body = new HashMap<String, Object>();
        body.put("fromAccountId", aliceAccount.getId());
        body.put("toAccountNumber", payeeAccountNumber);
        body.put("amount", new BigDecimal(amount));
        body.put("currency", "EUR");
        body.put("description", description);
        body.put("category", category);
        var result =
                api.post("/api/v1/transfers", aliceToken, body, Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }
}
