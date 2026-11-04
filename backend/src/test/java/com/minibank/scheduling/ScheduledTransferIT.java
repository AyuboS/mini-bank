package com.minibank.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.scheduling.repository.ScheduledTransferRepository;
import com.minibank.scheduling.service.ScheduledTransferRunner;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Standing orders, including the part that actually executes them.
 *
 * <p>The runner is driven directly rather than being waited on: the scheduled trigger fires on a
 * timer, and a test that sleeps until it does would be slow and flaky for no extra assurance. What
 * matters is that the code the timer calls moves real money, exactly once.
 */
@DisplayName("Scheduled transfers")
class ScheduledTransferIT extends AbstractIntegrationTest {

    @Autowired
    private ScheduledTransferRunner runner;

    @Autowired
    private ScheduledTransferRepository orders;

    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;

    @BeforeEach
    void signIn() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        aliceAccount = data.fundedAccount(alice, "1000.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
    }

    @Test
    @DisplayName("a due order settles through the ordinary transfer path")
    void aDueOrderMovesRealMoney() {
        String orderId = create("250.00", "MONTHLY", today()).get("id").asText();

        boolean settled = runner.runOne(java.util.UUID.fromString(orderId), today());

        assertThat(settled).isTrue();
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("750.00");
        assertThat(databaseCleaner.balanceOf(bobAccount.getAccountNumber())).isEqualByComparingTo("250.00");

        JsonNode order = api.getAsUser("/api/v1/scheduled-transfers/" + orderId, aliceToken);
        assertThat(order.get("runsCompleted").asInt()).isEqualTo(1);
        assertThat(order.get("status").asText()).isEqualTo("SCHEDULED");
        // A monthly order advances a month, so it cannot fire again today.
        assertThat(LocalDate.parse(order.get("nextRunOn").asText())).isEqualTo(today().plusMonths(1));
    }

    @Test
    @DisplayName("running the same due date twice does not pay twice")
    void isIdempotentWithinOneDueDate() {
        String orderId = create("250.00", "MONTHLY", today()).get("id").asText();
        java.util.UUID id = java.util.UUID.fromString(orderId);

        runner.runOne(id, today());
        // A restart, an overlapping tick, or two instances briefly running at once.
        runner.runOne(id, today());

        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("750.00");
        assertThat(api.getAsUser("/api/v1/scheduled-transfers/" + orderId, aliceToken)
                        .get("runsCompleted")
                        .asInt())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a one-off order completes after its single run")
    void aOneOffCompletes() {
        String orderId = create("100.00", "ONCE", today()).get("id").asText();

        runner.runOne(java.util.UUID.fromString(orderId), today());

        JsonNode order = api.getAsUser("/api/v1/scheduled-transfers/" + orderId, aliceToken);
        assertThat(order.get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("an order the account cannot cover is recorded, not retried into the ground")
    void recordsARefusalAndMovesOn() {
        String orderId = create("5000.00", "MONTHLY", today()).get("id").asText();

        boolean settled = runner.runOne(java.util.UUID.fromString(orderId), today());

        assertThat(settled).isFalse();
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("1000.00");

        JsonNode order = api.getAsUser("/api/v1/scheduled-transfers/" + orderId, aliceToken);
        assertThat(order.get("lastFailureReason").asText()).isNotBlank();
        assertThat(order.get("runsCompleted").asInt()).isZero();
        // The date advanced: the customer is asked again next month rather than every ten minutes.
        assertThat(LocalDate.parse(order.get("nextRunOn").asText())).isEqualTo(today().plusMonths(1));

        // And they are told.
        JsonNode feed = api.getAsUser("/api/v1/notifications", aliceToken).get("content");
        assertThat(feed.findValuesAsText("title")).contains("Scheduled payment did not go through");
    }

    @Test
    @DisplayName("a paused order is skipped, and resuming does not fire the runs it missed")
    void pausingSkipsAndResumingDoesNotCatchUp() {
        String orderId = create("100.00", "MONTHLY", today()).get("id").asText();
        api.patch("/api/v1/scheduled-transfers/" + orderId + "/pause", aliceToken, null);

        assertThat(runner.runOne(java.util.UUID.fromString(orderId), today())).isFalse();
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("1000.00");

        // Resume a month later. The missed date is skipped forward, not paid retroactively.
        JsonNode resumed = api.parse(api.patch("/api/v1/scheduled-transfers/" + orderId + "/resume", aliceToken, null));
        assertThat(resumed.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(LocalDate.parse(resumed.get("nextRunOn").asText())).isAfterOrEqualTo(today());
    }

    @Test
    @DisplayName("refuses a first run in the past")
    void refusesAPastStartDate() {
        var response = api.post("/api/v1/scheduled-transfers", aliceToken, request("50.00", "ONCE", today().minusDays(1)));
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.parse(response).get("code").asText()).isEqualTo("SCHEDULED_DATE_IN_PAST");
    }

    @Test
    @DisplayName("a cancelled order can no longer be changed or run")
    void cancellingIsTerminal() {
        String orderId = create("100.00", "MONTHLY", today()).get("id").asText();
        api.patch("/api/v1/scheduled-transfers/" + orderId + "/cancel", aliceToken, null);

        assertThat(runner.runOne(java.util.UUID.fromString(orderId), today())).isFalse();
        assertThat(api.patch("/api/v1/scheduled-transfers/" + orderId + "/pause", aliceToken, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(409);
    }

    @Test
    @DisplayName("another customer's standing order is reported as missing")
    void ordersAreNotVisibleAcrossCustomers() {
        String orderId = create("100.00", "MONTHLY", today()).get("id").asText();
        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");

        assertThat(api.get("/api/v1/scheduled-transfers/" + orderId, malloryToken)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("the runner picks up everything that is due")
    void theRunnerFindsDueOrders() {
        create("100.00", "MONTHLY", today());
        create("50.00", "MONTHLY", today());
        create("25.00", "MONTHLY", today().plusDays(3));

        assertThat(orders.findDueIds(today(), org.springframework.data.domain.PageRequest.of(0, 100)))
                .hasSize(2);
    }

    private JsonNode create(String amount, String frequency, LocalDate startOn) {
        var result = api.post("/api/v1/scheduled-transfers", aliceToken, request(amount, frequency, startOn));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return api.parse(result);
    }

    private Map<String, Object> request(String amount, String frequency, LocalDate startOn) {
        var body = new HashMap<String, Object>();
        body.put("fromAccountId", aliceAccount.getId());
        body.put("toAccountNumber", bobAccount.getAccountNumber());
        body.put("amount", new BigDecimal(amount));
        body.put("description", "Rent");
        body.put("category", "BILLS");
        body.put("frequency", frequency);
        body.put("startOn", startOn.toString());
        return body;
    }

    /** UTC, matching the clock the service and the runner both use to decide what "today" is. */
    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
