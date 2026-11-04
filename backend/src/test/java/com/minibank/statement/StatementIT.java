package com.minibank.statement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Monthly statements.
 *
 * <p>The one assertion that matters is reconciliation: opening + in − out must equal closing. The
 * opening balance is derived from the ledger rather than stored, so a statement that fails to add up
 * would mean the derivation is wrong — which is the only way it can be wrong.
 */
@DisplayName("Statements")
class StatementIT extends AbstractIntegrationTest {

    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;

    @BeforeEach
    void signIn() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        aliceAccount = data.fundedAccount(alice, "1000.00");
        bobAccount = data.fundedAccount(bob, "500.00");
        aliceToken = api.loginAsTestUser("alice");
    }

    @Test
    @DisplayName("opening + in − out equals closing")
    void reconciles() {
        pay(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "120.00", "Rent");
        pay(api.loginAsTestUser("bob"), bobAccount, aliceAccount.getAccountNumber(), "45.00", "Split");

        JsonNode statement = api.getAsUser(currentStatementPath(), aliceToken);

        BigDecimal opening = statement.get("openingBalance").decimalValue();
        BigDecimal in = statement.get("totalIn").decimalValue();
        BigDecimal out = statement.get("totalOut").decimalValue();
        BigDecimal closing = statement.get("closingBalance").decimalValue();

        assertThat(opening.add(in).subtract(out)).isEqualByComparingTo(closing);
        // And the closing balance is the balance the account actually holds.
        assertThat(closing).isEqualByComparingTo(databaseCleaner.balanceOf(aliceAccount.getAccountNumber()));
    }

    @Test
    @DisplayName("a month with no movement opens and closes at the same figure")
    void handlesAQuietMonth() {
        // Two months back: the account did not exist, so both figures are zero rather than absent.
        String path = "/api/v1/accounts/%s/statements?month=%s"
                .formatted(aliceAccount.getId(), YearMonth.now(ZoneOffset.UTC).minusMonths(2));

        JsonNode statement = api.getAsUser(path, aliceToken);

        assertThat(statement.get("transactionCount").asInt()).isZero();
        assertThat(statement.get("openingBalance").decimalValue())
                .isEqualByComparingTo(statement.get("closingBalance").decimalValue());
    }

    @Test
    @DisplayName("exports a CSV with the right headers and a download disposition")
    void exportsCsv() throws Exception {
        pay(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "48.20", "Grocery store");

        var result = api.get(currentStatementPath().replace("/statements?", "/statements/export?"), aliceToken);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        // `attachment`, not `inline`: serving user-authored content inline is how a download turns
        // into an XSS vector.
        assertThat(result.getResponse().getHeader("Content-Disposition")).startsWith("attachment;");

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("MBank statement");
        assertThat(csv).contains("Date,Reference,Description,Category,Counterparty,Direction,Amount,Currency,Status");
        assertThat(csv).contains("Grocery store");
        // Outgoing money is written negative, so a column total is the period's net movement.
        assertThat(csv).contains(",OUT,-48.20,EUR,");
    }

    @Test
    @DisplayName("a description that looks like a formula is neutralised in the export")
    void neutralisesFormulasFromDescriptions() throws Exception {
        pay(aliceToken, aliceAccount, bobAccount.getAccountNumber(), "10.00", "=1+1");

        String csv = api.get(currentStatementPath().replace("/statements?", "/statements/export?"), aliceToken)
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("'=1+1");
    }

    @Test
    @DisplayName("another customer's statement is not reachable")
    void statementsAreNotReachableAcrossCustomers() {
        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");

        assertThat(api.get(currentStatementPath(), malloryToken).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(api.get(currentStatementPath().replace("/statements?", "/statements/export?"), malloryToken)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    private String currentStatementPath() {
        return "/api/v1/accounts/%s/statements?month=%s"
                .formatted(aliceAccount.getId(), YearMonth.now(ZoneOffset.UTC));
    }

    private void pay(String token, BankAccount from, String toAccountNumber, String amount, String description) {
        var body = new HashMap<String, Object>();
        body.put("fromAccountId", from.getId());
        body.put("toAccountNumber", toAccountNumber);
        body.put("amount", new BigDecimal(amount));
        body.put("currency", "EUR");
        body.put("description", description);
        var result =
                api.post("/api/v1/transfers", token, body, Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }
}
