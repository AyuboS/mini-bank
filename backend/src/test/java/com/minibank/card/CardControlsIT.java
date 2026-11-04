package com.minibank.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.account.domain.BankAccount;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Per-card usage controls and the caps that go with them. */
@DisplayName("Card controls")
class CardControlsIT extends AbstractIntegrationTest {

    private String aliceToken;
    private String cardId;

    @BeforeEach
    void issueACard() {
        User alice = data.customer("alice");
        BankAccount account = data.fundedAccount(alice, "1000.00");
        aliceToken = api.loginAsTestUser("alice");

        cardId = api.parse(api.post(
                        "/api/v1/cards",
                        aliceToken,
                        Map.of("accountId", account.getId(), "type", "VIRTUAL", "design", "AURORA", "label", "Online")))
                .get("id")
                .asText();
    }

    @Test
    @DisplayName("a new card has every channel on except the magnetic stripe")
    void defaultsAreSafe() {
        JsonNode controls = api.getAsUser("/api/v1/cards/" + cardId, aliceToken).get("controls");

        assertThat(controls.get("onlinePayments").asBoolean()).isTrue();
        assertThat(controls.get("contactless").asBoolean()).isTrue();
        assertThat(controls.get("atmWithdrawals").asBoolean()).isTrue();
        assertThat(controls.get("international").asBoolean()).isTrue();
        // The one channel with no cryptographic authentication starts off and must be asked for.
        assertThat(controls.get("magneticStripe").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("sending one switch leaves the others alone")
    void aPartialUpdateIsPartial() {
        JsonNode after = api.parse(api.patch(
                "/api/v1/cards/" + cardId + "/controls", aliceToken, Map.of("onlinePayments", false)));

        JsonNode controls = after.get("controls");
        assertThat(controls.get("onlinePayments").asBoolean()).isFalse();
        // The fields the client did not mention are untouched. With primitives instead of Booleans,
        // this request would have switched the whole card off.
        assertThat(controls.get("contactless").asBoolean()).isTrue();
        assertThat(controls.get("atmWithdrawals").asBoolean()).isTrue();
        assertThat(controls.get("international").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("switches can be turned back on")
    void switchesAreReversible() {
        api.patch("/api/v1/cards/" + cardId + "/controls", aliceToken, Map.of("international", false));
        JsonNode after = api.parse(
                api.patch("/api/v1/cards/" + cardId + "/controls", aliceToken, Map.of("international", true)));

        assertThat(after.get("controls").get("international").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("accepts ATM and online caps under the daily cap")
    void acceptsChannelCaps() {
        var body = new HashMap<String, Object>();
        body.put("dailyLimit", "500.00");
        body.put("monthlyLimit", "3000.00");
        body.put("atmLimit", "200.00");
        body.put("onlineLimit", "300.00");

        JsonNode controls = api.parse(api.patch("/api/v1/cards/" + cardId + "/limits", aliceToken, body))
                .get("controls");

        assertThat(controls.get("dailyLimit").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(controls.get("atmLimit").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(controls.get("onlineLimit").decimalValue()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("refuses a channel cap above the daily cap, which could never bind")
    void refusesAnUnreachableChannelCap() {
        var body = new HashMap<String, Object>();
        body.put("dailyLimit", "100.00");
        body.put("atmLimit", "500.00");

        var response = api.patch("/api/v1/cards/" + cardId + "/limits", aliceToken, body);

        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.parse(response).get("code").asText()).isEqualTo("CARD_INVALID_LIMITS");
    }

    @Test
    @DisplayName("clearing a cap needs the explicit flag, not just a missing value")
    void clearingIsExplicit() {
        var set = new HashMap<String, Object>();
        set.put("dailyLimit", "500.00");
        api.patch("/api/v1/cards/" + cardId + "/limits", aliceToken, set);

        // An empty body means "change nothing", not "remove everything".
        JsonNode unchanged = api.parse(api.patch("/api/v1/cards/" + cardId + "/limits", aliceToken, Map.of()));
        assertThat(unchanged.get("controls").get("dailyLimit").decimalValue()).isEqualByComparingTo("500.00");

        JsonNode cleared = api.parse(
                api.patch("/api/v1/cards/" + cardId + "/limits", aliceToken, Map.of("clearDailyLimit", true)));
        // The API omits null properties rather than serialising them, so a cleared cap is an absent
        // field. Asserting on `has` rather than on `isNull` is what actually holds.
        assertThat(cleared.get("controls").has("dailyLimit")).isFalse();
    }

    @Test
    @DisplayName("another customer cannot change my card's controls")
    void controlsAreNotReachableAcrossCustomers() {
        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");

        assertThat(api.patch("/api/v1/cards/" + cardId + "/controls", malloryToken, Map.of("onlinePayments", false))
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);

        // And nothing changed.
        assertThat(api.getAsUser("/api/v1/cards/" + cardId, aliceToken)
                        .get("controls")
                        .get("onlinePayments")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("a control change is written to the audit trail")
    void changesAreAudited() {
        api.patch("/api/v1/cards/" + cardId + "/controls", aliceToken, Map.of("magneticStripe", true));

        User admin = data.admin("auditor");
        String adminToken = api.loginAsTestUser("auditor");
        JsonNode audit = api.getAsUser("/api/v1/admin/audit-logs?action=CARD_CONTROLS_UPDATED", adminToken);

        assertThat(audit.get("content")).isNotEmpty();
        assertThat(admin.getUsername()).isEqualTo("auditor");
    }
}
