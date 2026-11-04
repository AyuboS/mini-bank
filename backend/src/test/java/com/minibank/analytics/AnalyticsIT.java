package com.minibank.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spending analytics.
 *
 * <p>Two properties matter more than any individual figure, and both are asserted here:
 *
 * <ul>
 *   <li>money moved between the customer's own accounts is neither income nor spending
 *   <li>an insight is never produced when the data cannot support it
 * </ul>
 */
@DisplayName("Analytics")
class AnalyticsIT extends AbstractIntegrationTest {

    private User alice;
    private BankAccount aliceAccount;
    private String aliceToken;

    @BeforeEach
    void signIn() {
        alice = data.customer("alice");
        aliceAccount = data.fundedAccount(alice, "2000.00");
        aliceToken = api.loginAsTestUser("alice");
    }

    @Test
    @DisplayName("separates income, spending and movement between the customer's own accounts")
    void separatesIncomeSpendingAndInternalMovement() {
        User bob = data.customer("bob");
        BankAccount bobAccount = data.fundedAccount(bob, "500.00");
        BankAccount aliceSecond = data.account(alice, com.minibank.common.money.CurrencyCode.EUR);

        // Out, to somebody else: spending.
        transfer(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "120.00", "Dinner", "DINING");
        // Between Alice's own accounts: movement, and neither of the other two.
        transfer(aliceToken, aliceAccount, aliceSecond.getAccountNumber(), "300.00", "Set aside", null);
        // In, from somebody else: income.
        transfer(api.loginAsTestUser("bob"), bobAccount, aliceAccount.getAccountNumber(), "80.00", "Split", null);

        JsonNode summary = api.getAsUser("/api/v1/analytics/summary?currency=EUR", aliceToken);

        assertThat(summary.get("spending").decimalValue()).isEqualByComparingTo("120.00");
        // 2000 of opening balance plus 80 from Bob. The opening deposit comes from the bank's own
        // system account, which Alice does not own, so it is income — and should be: money she did
        // not have and now does is exactly what the figure means.
        assertThat(summary.get("income").decimalValue()).isEqualByComparingTo("2080.00");
        // The 300 Alice moved between her own two accounts is in neither total. This is the
        // assertion the whole design rests on: saving is not spending.
        assertThat(summary.get("internalMovement").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(summary.get("net").decimalValue()).isEqualByComparingTo("1960.00");
    }

    @Test
    @DisplayName("breaks spending down by category, largest first, with shares that sum to the whole")
    void breaksSpendingDownByCategory() {
        User bob = data.customer("bob");
        BankAccount bobAccount = data.fundedAccount(bob, "0.00");

        transfer(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "300.00", "Rent", "BILLS");
        transfer(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "100.00", "Shop", "GROCERIES");
        transfer(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "100.00", "More shopping", "GROCERIES");

        JsonNode byCategory = api.getAsUser("/api/v1/analytics/summary?currency=EUR", aliceToken).get("byCategory");

        assertThat(byCategory).hasSize(2);
        // Largest first.
        assertThat(byCategory.get(0).get("category").asText()).isEqualTo("BILLS");
        assertThat(byCategory.get(0).get("amount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(byCategory.get(0).get("share").decimalValue()).isEqualByComparingTo("60.0");

        assertThat(byCategory.get(1).get("category").asText()).isEqualTo("GROCERIES");
        assertThat(byCategory.get(1).get("transactionCount").asInt()).isEqualTo(2);
        assertThat(byCategory.get(1).get("share").decimalValue()).isEqualByComparingTo("40.0");
    }

    @Test
    @DisplayName("emits one point per day of the month, including the days with nothing in them")
    void emitsAnEvenDailySeries() {
        JsonNode summary = api.getAsUser("/api/v1/analytics/summary?month=2026-02&currency=EUR", aliceToken);

        // February 2026 has 28 days. A sparse series would leave the client to guess the gaps.
        assertThat(summary.get("series")).hasSize(28);
        assertThat(summary.get("series").get(0).get("date").asText()).isEqualTo("2026-02-01");
        assertThat(summary.get("series").get(27).get("date").asText()).isEqualTo("2026-02-28");
    }

    @Test
    @DisplayName("says nothing at all when there is nothing true to say")
    void producesNoInsightsWithoutData() {
        data.customer("newcomer");
        String token = api.loginAsTestUser("newcomer");

        // No accounts, no transactions, and therefore no observations. An "insight" here would be
        // an invention, which is exactly what this endpoint must never produce.
        assertThat(api.getAsUser("/api/v1/analytics/insights", token)).isEmpty();
    }

    @Test
    @DisplayName("insights are arithmetic the customer can check against the summary")
    void insightsMatchTheSummary() {
        User bob = data.customer("bob");
        BankAccount bobAccount = data.fundedAccount(bob, "0.00");
        transfer(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "250.00", "Rent", "BILLS");

        JsonNode insights = api.getAsUser("/api/v1/analytics/insights?currency=EUR", aliceToken);
        assertThat(insights).isNotEmpty();

        JsonNode topCategory = null;
        for (JsonNode insight : insights) {
            if ("TOP_CATEGORY".equals(insight.get("kind").asText())) {
                topCategory = insight;
            }
        }
        assertThat(topCategory).isNotNull();
        // Figures, not a finished sentence: the wording is composed by the client,
        // which is the only side that knows the reader's locale.
        assertThat(topCategory.get("category").asText()).isEqualTo("BILLS");
        assertThat(topCategory.get("amount").decimalValue()).isEqualByComparingTo("250.00");
        // 100% of spending, because it is the only outgoing payment there is.
        assertThat(topCategory.get("share").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(topCategory.get("tone").asText()).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("one customer's analytics never include another's transactions")
    void analyticsAreScopedToTheCaller() {
        User bob = data.customer("bob");
        BankAccount bobAccount = data.fundedAccount(bob, "900.00");
        String bobToken = api.loginAsTestUser("bob");

        transfer(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "400.00", "Rent", "BILLS");

        // The same movement: spending for Alice, income for Bob. Neither sees the other's side as
        // their own. Bob's income is 900 — his own 900 opening balance plus Alice's 400 — and
        // crucially contains nothing of Alice's spending.
        JsonNode bobSummary = api.getAsUser("/api/v1/analytics/summary?currency=EUR", bobToken);
        assertThat(bobSummary.get("spending").decimalValue()).isEqualByComparingTo("0");
        assertThat(bobSummary.get("income").decimalValue()).isEqualByComparingTo("1300.00");
    }

    private void transfer(
            String token, BankAccount from, String toAccountNumber, String amount, String description, String category) {
        var body = new java.util.HashMap<String, Object>();
        body.put("fromAccountId", from.getId());
        body.put("toAccountNumber", toAccountNumber);
        body.put("amount", new java.math.BigDecimal(amount));
        body.put("currency", "EUR");
        body.put("description", description);
        body.put("category", category);
        var result = api.post(
                "/api/v1/transfers", token, body, Map.of("Idempotency-Key", java.util.UUID.randomUUID().toString()));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }
}
