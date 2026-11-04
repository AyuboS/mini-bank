package com.minibank.notification;

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

/** The in-app notification feed. */
@DisplayName("Notifications")
class NotificationIT extends AbstractIntegrationTest {

    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void signIn() {
        User alice = data.customer("alice");
        User bob = data.customer("bob");
        aliceAccount = data.fundedAccount(alice, "1000.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
        bobToken = api.loginAsTestUser("bob");
    }

    @Test
    @DisplayName("a settled transfer notifies both sides")
    void notifiesBothSidesOfATransfer() {
        pay("120.00", "Rent");

        // The payer sees money leaving.
        JsonNode payerFeed = api.getAsUser("/api/v1/notifications", aliceToken).get("content");
        assertThat(payerFeed.findValuesAsText("title")).anyMatch(title -> title.contains("sent"));

        // The payee sees money arriving. Notifying only the payer would leave a customer to
        // discover an incoming payment by chance.
        JsonNode payeeFeed = api.getAsUser("/api/v1/notifications", bobToken).get("content");
        assertThat(payeeFeed.findValuesAsText("title")).anyMatch(title -> title.contains("received"));
    }

    @Test
    @DisplayName("the unread badge counts what has not been read")
    void countsUnread() {
        pay("10.00", "One");
        pay("20.00", "Two");

        assertThat(api.getAsUser("/api/v1/notifications/unread-count", aliceToken)
                        .get("unread")
                        .asInt())
                .isEqualTo(2);

        String firstId = api.getAsUser("/api/v1/notifications", aliceToken)
                .get("content")
                .get(0)
                .get("id")
                .asText();
        api.post("/api/v1/notifications/" + firstId + "/read", aliceToken, null);

        assertThat(api.getAsUser("/api/v1/notifications/unread-count", aliceToken)
                        .get("unread")
                        .asInt())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("marking everything read is idempotent")
    void marksAllRead() {
        pay("10.00", "One");
        pay("20.00", "Two");

        assertThat(api.parse(api.post("/api/v1/notifications/read-all", aliceToken, null))
                        .get("marked")
                        .asInt())
                .isEqualTo(2);
        // A second call touches nothing rather than re-marking.
        assertThat(api.parse(api.post("/api/v1/notifications/read-all", aliceToken, null))
                        .get("marked")
                        .asInt())
                .isZero();
        assertThat(api.getAsUser("/api/v1/notifications/unread-count", aliceToken)
                        .get("unread")
                        .asInt())
                .isZero();
    }

    @Test
    @DisplayName("filters by type and by unread")
    void filters() {
        pay("10.00", "One");

        assertThat(api.getAsUser("/api/v1/notifications?type=TRANSACTION", aliceToken).get("content"))
                .isNotEmpty();
        assertThat(api.getAsUser("/api/v1/notifications?type=SECURITY", aliceToken).get("content"))
                .isEmpty();
        assertThat(api.getAsUser("/api/v1/notifications?unreadOnly=true", aliceToken).get("content"))
                .isNotEmpty();
    }

    @Test
    @DisplayName("one customer cannot read or mark another's notifications")
    void notificationsAreScopedToTheirOwner() {
        pay("10.00", "One");
        String aliceNotificationId = api.getAsUser("/api/v1/notifications", aliceToken)
                .get("content")
                .get(0)
                .get("id")
                .asText();

        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");

        assertThat(api.getAsUser("/api/v1/notifications", malloryToken).get("content")).isEmpty();
        assertThat(api.post("/api/v1/notifications/" + aliceNotificationId + "/read", malloryToken, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    private void pay(String amount, String description) {
        var body = new HashMap<String, Object>();
        body.put("fromAccountId", aliceAccount.getId());
        body.put("toAccountNumber", bobAccount.getAccountNumber());
        body.put("amount", new BigDecimal(amount));
        body.put("currency", "EUR");
        body.put("description", description);
        var result =
                api.post("/api/v1/transfers", aliceToken, body, Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }
}
