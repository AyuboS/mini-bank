package com.minibank.supportcentre;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Support tickets. */
@DisplayName("Support centre")
class SupportIT extends AbstractIntegrationTest {

    private String aliceToken;

    @BeforeEach
    void signIn() {
        data.customer("alice");
        aliceToken = api.loginAsTestUser("alice");
    }

    @Test
    @DisplayName("opening a ticket gives a reference and records the description as the first message")
    void opensATicket() {
        JsonNode ticket = open("My card was declined", "CARD", "It was declined at a shop this morning.");

        assertThat(ticket.get("reference").asText()).matches("^MB-SUP-[A-Z2-9]{6}$");
        assertThat(ticket.get("status").asText()).isEqualTo("OPEN");

        JsonNode full = api.getAsUser("/api/v1/support/tickets/" + ticket.get("id").asText(), aliceToken);
        assertThat(full.get("messages")).hasSize(1);
        assertThat(full.get("messages").get(0).get("fromStaff").asBoolean()).isFalse();
        assertThat(full.get("messages").get(0).get("body").asText())
                .isEqualTo("It was declined at a shop this morning.");
    }

    @Test
    @DisplayName("replies append to the thread in order")
    void repliesAppendToTheThread() {
        String id = open("Question", "APP", "First message").get("id").asText();

        api.post("/api/v1/support/tickets/" + id + "/messages", aliceToken, Map.of("body", "Second message"));
        JsonNode full = api.getAsUser("/api/v1/support/tickets/" + id, aliceToken);

        assertThat(full.get("messages")).hasSize(2);
        assertThat(full.get("messages").get(0).get("body").asText()).isEqualTo("First message");
        assertThat(full.get("messages").get(1).get("body").asText()).isEqualTo("Second message");
    }

    @Test
    @DisplayName("a closed ticket takes no more replies")
    void aClosedTicketIsClosed() {
        String id = open("Question", "APP", "Body").get("id").asText();
        api.patch("/api/v1/support/tickets/" + id + "/close", aliceToken, null);

        var response =
                api.post("/api/v1/support/tickets/" + id + "/messages", aliceToken, Map.of("body", "One more thing"));

        assertThat(response.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.parse(response).get("code").asText()).isEqualTo("SUPPORT_TICKET_CLOSED");
    }

    @Test
    @DisplayName("another customer's ticket is reported as missing")
    void ticketsAreNotVisibleAcrossCustomers() {
        String id = open("Private", "SECURITY", "Sensitive detail").get("id").asText();

        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");

        assertThat(api.get("/api/v1/support/tickets/" + id, malloryToken)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
        assertThat(api.getAsUser("/api/v1/support/tickets", malloryToken)).isEmpty();
    }

    @Test
    @DisplayName("nothing answers the ticket automatically")
    void nothingAutoReplies() {
        String id = open("Question", "APP", "Body").get("id").asText();

        // A generated "we understand your concern" would be worse than silence: it would make a
        // customer with an urgent problem believe they had been heard.
        JsonNode full = api.getAsUser("/api/v1/support/tickets/" + id, aliceToken);
        assertThat(full.get("messages")).hasSize(1);
        assertThat(full.get("messages").findValues("fromStaff")).allMatch(node -> !node.asBoolean());
    }

    private JsonNode open(String subject, String category, String description) {
        var result = api.post(
                "/api/v1/support/tickets",
                aliceToken,
                Map.of("subject", subject, "category", category, "description", description));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return api.parse(result);
    }
}
