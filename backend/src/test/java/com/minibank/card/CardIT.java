package com.minibank.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.card.service.CardNumberGenerator;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The card API end to end.
 *
 * <p>The assertions that matter most here are the negative ones: that a list response never carries
 * a full number, that another customer's card is invisible rather than merely forbidden, and that
 * the reveal endpoint is the only way to obtain card details at all.
 */
class CardIT extends AbstractIntegrationTest {

    private static final String CARDS = "/api/v1/cards";

    private User alice;
    private String aliceToken;

    @BeforeEach
    void signIn() {
        alice = data.customer("card.alice");
        data.fundedAccount(alice, "1000.00");
        aliceToken = api.loginAsTestUser("card.alice");
    }

    // --- issuing -------------------------------------------------------------

    @Test
    @DisplayName("a customer can order a card and it comes back masked")
    void issuesACard() {
        var result = api.post(CARDS, aliceToken, Map.of("type", "PHYSICAL", "design", "AURORA"));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());

        JsonNode card = api.parse(result);
        assertThat(card.get("type").asText()).isEqualTo("PHYSICAL");
        assertThat(card.get("design").asText()).isEqualTo("AURORA");
        assertThat(card.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(card.get("network").asText()).isEqualTo("VISA");
        assertThat(card.get("lastFour").asText()).hasSize(4);
        assertThat(card.get("maskedNumber").asText()).contains("••••");
        assertThat(card.get("cardholderName").asText()).isEqualTo("TEST USER" + suffixOf(alice));
        // Four years out, which is the issuing period the entity applies.
        assertThat(card.get("expirationYear").asInt()).isGreaterThan(java.time.Year.now().getValue());
    }

    @Test
    @DisplayName("a virtual card is distinguishable from a physical one")
    void issuesAVirtualCard() {
        JsonNode card = api.parse(api.post(CARDS, aliceToken, Map.of("type", "VIRTUAL", "design", "CRYSTAL")));
        assertThat(card.get("type").asText()).isEqualTo("VIRTUAL");
        assertThat(card.get("label").asText()).isEqualTo("Crystal virtual");
    }

    @Test
    @DisplayName("no response outside the reveal endpoint contains a full card number")
    void neverLeaksTheNumber() {
        api.post(CARDS, aliceToken, Map.of("type", "VIRTUAL", "design", "OCEAN"));

        String listBody = api.getAsUser(CARDS, aliceToken).toString();
        // Sixteen consecutive digits anywhere in the payload would be a leaked number.
        assertThat(listBody).doesNotMatch("(?s).*\\d{16}.*");
    }

    @Test
    @DisplayName("the wallet is capped")
    void enforcesTheCardLimit() {
        for (int i = 0; i < 8; i++) {
            api.post(CARDS, aliceToken, Map.of("type", "VIRTUAL", "design", "CARBON"));
        }
        var refused = api.post(CARDS, aliceToken, Map.of("type", "VIRTUAL", "design", "CARBON"));
        assertThat(refused.getResponse().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(api.parse(refused).get("code").asText()).isEqualTo("CARD_LIMIT_REACHED");
    }

    @Test
    @DisplayName("a design the client invented is rejected")
    void rejectsAnUnknownDesign() {
        var result = api.post(CARDS, aliceToken, Map.of("type", "VIRTUAL", "design", "GOLD_PLATED"));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    // --- listing -------------------------------------------------------------

    @Test
    @DisplayName("listing returns the caller's cards, newest first")
    void listsOwnCards() {
        api.post(CARDS, aliceToken, Map.of("type", "PHYSICAL", "design", "AURORA", "label", "First"));
        api.post(CARDS, aliceToken, Map.of("type", "VIRTUAL", "design", "SUNSET", "label", "Second"));

        JsonNode cards = api.getAsUser(CARDS, aliceToken);
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).get("label").asText()).isEqualTo("Second");
    }

    // --- freezing ------------------------------------------------------------

    @Test
    @DisplayName("a card can be frozen and unfrozen")
    void freezesAndUnfreezes() {
        String cardId = issueCard();

        JsonNode frozen = api.parse(api.patch(CARDS + "/" + cardId + "/freeze", aliceToken, null));
        assertThat(frozen.get("status").asText()).isEqualTo("FROZEN");
        assertThat(frozen.get("frozenAt").asText()).isNotBlank();

        JsonNode thawed = api.parse(api.patch(CARDS + "/" + cardId + "/unfreeze", aliceToken, null));
        assertThat(thawed.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(thawed.has("frozenAt")).isFalse();
    }

    @Test
    @DisplayName("a cancelled card cannot be frozen")
    void refusesToFreezeACancelledCard() {
        String cardId = issueCard();
        api.delete(CARDS + "/" + cardId, aliceToken);

        var result = api.patch(CARDS + "/" + cardId + "/freeze", aliceToken, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("a cancelled card disappears from the wallet")
    void hidesCancelledCards() {
        String cardId = issueCard();
        assertThat(api.delete(CARDS + "/" + cardId, aliceToken).getResponse().getStatus())
                .isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(api.getAsUser(CARDS, aliceToken)).isEmpty();
    }

    // --- ownership -----------------------------------------------------------

    @Test
    @DisplayName("somebody else's card is not found, not forbidden")
    void hidesOtherCustomersCards() {
        String aliceCard = issueCard();

        data.fundedAccount(data.customer("card.bob"), "100.00");
        String bobToken = api.loginAsTestUser("card.bob");

        // 404 rather than 403: a 403 would confirm the id exists.
        for (String path : new String[] {"", "/spending", "/transactions"}) {
            assertThat(api.get(CARDS + "/" + aliceCard + path, bobToken)
                            .getResponse()
                            .getStatus())
                    .as("GET %s", path)
                    .isEqualTo(HttpStatus.NOT_FOUND.value());
        }
        assertThat(api.patch(CARDS + "/" + aliceCard + "/freeze", bobToken, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(api.post(CARDS + "/" + aliceCard + "/details", bobToken, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("an administrator has no route into a customer's cards either")
    void deniesAdministratorsToo() {
        String aliceCard = issueCard();
        data.admin("card.admin");
        String adminToken = api.loginAsTestUser("card.admin");

        assertThat(api.post(CARDS + "/" + aliceCard + "/details", adminToken, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("the endpoints require a session")
    void requiresAuthentication() {
        assertThat(api.get(CARDS, null).getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    // --- details -------------------------------------------------------------

    @Test
    @DisplayName("revealing details returns a Luhn-valid number and a derived verification value")
    void revealsDetailsOnRequest() {
        String cardId = issueCard();

        var result = api.post(CARDS + "/" + cardId + "/details", aliceToken, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        // Nothing between the server and the browser should keep a copy.
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");

        JsonNode details = api.parse(result);
        String number = details.get("number").asText();
        assertThat(number).hasSize(16).containsOnlyDigits();
        assertThat(CardNumberGenerator.isLuhnValid(number)).isTrue();
        assertThat(details.get("verificationValue").asText()).hasSize(3).containsOnlyDigits();
        // The contract says so out loud, so a client cannot present these as real credentials.
        assertThat(details.get("simulated").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("the revealed number ends with the digits the wallet showed")
    void revealMatchesTheMaskedCard() {
        JsonNode card = api.parse(api.post(CARDS, aliceToken, Map.of("type", "VIRTUAL", "design", "AURORA")));
        JsonNode details = api.parse(api.post(CARDS + "/" + card.get("id").asText() + "/details", aliceToken, null));

        assertThat(details.get("number").asText()).endsWith(card.get("lastFour").asText());
    }

    @Test
    @DisplayName("every reveal is written to the audit trail")
    void auditsEveryReveal() {
        String cardId = issueCard();
        int before = databaseCleaner.countRows("audit_logs");

        api.post(CARDS + "/" + cardId + "/details", aliceToken, null);

        assertThat(databaseCleaner.countRows("audit_logs")).isGreaterThan(before);
    }

    // --- updates and limits ---------------------------------------------------

    @Test
    @DisplayName("a card can be renamed and restyled")
    void updatesLabelAndDesign() {
        String cardId = issueCard();
        JsonNode updated = api.parse(
                api.patch(CARDS + "/" + cardId, aliceToken, Map.of("label", "Holiday", "design", "SUNSET")));

        assertThat(updated.get("label").asText()).isEqualTo("Holiday");
        assertThat(updated.get("design").asText()).isEqualTo("SUNSET");
    }

    @Test
    @DisplayName("spending limits are stored and reported against usage")
    void setsSpendingLimits() {
        String cardId = issueCard();
        JsonNode updated = api.parse(api.patch(
                CARDS + "/" + cardId + "/limits",
                aliceToken,
                Map.of("dailyLimit", "1000.00", "monthlyLimit", "5000.00")));

        assertThat(updated.get("dailyLimit").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(updated.get("monthlyLimit").decimalValue()).isEqualByComparingTo("5000.00");

        JsonNode spending = api.getAsUser(CARDS + "/" + cardId + "/spending", aliceToken);
        assertThat(spending.get("dailyLimit").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(spending.get("spentToday").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(spending.get("currency").asText()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("a monthly limit below the daily one is refused")
    void rejectsContradictoryLimits() {
        String cardId = issueCard();
        var result = api.patch(
                CARDS + "/" + cardId + "/limits",
                aliceToken,
                Map.of("dailyLimit", "1000.00", "monthlyLimit", "500.00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("CARD_INVALID_LIMITS");
    }

    @Test
    @DisplayName("a limit can be cleared")
    void clearsALimit() {
        String cardId = issueCard();
        api.patch(CARDS + "/" + cardId + "/limits", aliceToken, Map.of("dailyLimit", "1000.00"));

        JsonNode cleared = api.parse(api.patch(
                CARDS + "/" + cardId + "/limits", aliceToken, Map.of("clearDailyLimit", true)));
        assertThat(cleared.has("dailyLimit")).isFalse();
    }

    // --- transactions ---------------------------------------------------------

    @Test
    @DisplayName("a card lists the transactions of the account behind it")
    void listsAccountTransactions() {
        String cardId = issueCard();
        JsonNode page = api.getAsUser(CARDS + "/" + cardId + "/transactions", aliceToken);

        // The opening balance deposit is the account's one movement so far.
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("direction").asText()).isEqualTo("CREDIT");
    }

    private String issueCard() {
        return api.parse(api.post(CARDS, aliceToken, Map.of("type", "PHYSICAL", "design", "AURORA")))
                .get("id")
                .asText();
    }

    /** TestDataFactory derives the surname from the username's hash. */
    private static String suffixOf(User user) {
        return user.getLastName().substring("User".length());
    }
}
