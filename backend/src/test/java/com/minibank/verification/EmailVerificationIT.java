package com.minibank.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.support.TestDataFactory;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Email verification end to end.
 *
 * <p>The token is read out of the intercepted message rather than out of the database, because the
 * database only holds a hash of it. Following the link the customer would follow is the only way to
 * test the feature rather than the assertion.
 */
class EmailVerificationIT extends AbstractIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String VERIFY = "/api/v1/auth/email/verify";

    @Test
    @DisplayName("registering sends a verification email with a working link")
    void sendsAVerificationEmailOnRegistration() {
        register("mail.alice", "alice.mail@example.com");

        var email = emails.lastTo("alice.mail@example.com").orElseThrow();
        assertThat(email.subject()).isEqualTo("Verify your MBank email");
        assertThat(email.textBody()).contains("MBank").contains("/verify-email?token=");
        // Both alternatives are real, not one duplicated into the other.
        assertThat(email.htmlBody()).contains("<a href=").contains("Verify email");
        // The message must never carry a credential.
        assertThat(email.textBody()).doesNotContain(TestDataFactory.PASSWORD);
        assertThat(email.htmlBody()).doesNotContain(TestDataFactory.PASSWORD);
    }

    @Test
    @DisplayName("a new account starts unverified and the link verifies it")
    void verifiesTheAddress() {
        JsonNode created = api.parse(register("mail.bob", "bob.mail@example.com"));
        assertThat(created.get("emailVerified").asBoolean()).isFalse();

        String token = emails.lastTokenFor("bob.mail@example.com").orElseThrow();
        var result = api.post(VERIFY, null, Map.of("token", token));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(api.parse(result).get("emailVerified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("the link works exactly once")
    void burnsTheToken() {
        register("mail.once", "once.mail@example.com");
        String token = emails.lastTokenFor("once.mail@example.com").orElseThrow();

        assertThat(api.post(VERIFY, null, Map.of("token", token)).getResponse().getStatus())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(api.post(VERIFY, null, Map.of("token", token)).getResponse().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("a token nobody issued is refused")
    void rejectsAnUnknownToken() {
        var result = api.post(VERIFY, null, Map.of("token", "not-a-token-anybody-ever-issued"));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("VERIFICATION_CODE_INVALID");
    }

    @Test
    @DisplayName("one customer's link does not verify another's address")
    void keepsTokensToTheirOwnAccount() {
        register("mail.first", "first.mail@example.com");
        String firstToken = emails.lastTokenFor("first.mail@example.com").orElseThrow();

        JsonNode second = api.parse(register("mail.second", "second.mail@example.com"));

        api.post(VERIFY, null, Map.of("token", firstToken));

        // The second account is untouched by the first one's link.
        String token = api.parse(api.post(
                        "/api/v1/auth/login",
                        null,
                        Map.of("username", "mail.second", "password", TestDataFactory.PASSWORD)))
                .get("accessToken")
                .asText();
        JsonNode profile = api.getAsUser("/api/v1/users/me", token);
        assertThat(profile.get("id").asText()).isEqualTo(second.get("id").asText());
        assertThat(profile.get("emailVerified").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a duplicate email is refused and no message is sent")
    void rejectsADuplicateAddress() {
        register("mail.original", "taken.mail@example.com");
        emails.clear();

        var result = api.post(
                REGISTER,
                null,
                Map.of(
                        "username", "mail.copycat",
                        "email", "taken.mail@example.com",
                        "password", TestDataFactory.PASSWORD,
                        "firstName", "Copy",
                        "lastName", "Cat"));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("EMAIL_ALREADY_EXISTS");
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    @DisplayName("asking again immediately is refused: the cooldown covers email too")
    void enforcesTheResendCooldown() {
        register("mail.resend", "resend.mail@example.com");
        emails.clear();

        String token = api.login("mail.resend", TestDataFactory.PASSWORD);
        var result = api.post("/api/v1/users/me/email/verify", token, null);

        // Registration has just sent one. A second within the cooldown would let the endpoint be
        // used to post mail at somebody, so it is refused rather than quietly ignored.
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    @DisplayName("asking again once verified sends nothing, so a live link cannot be invalidated")
    void doesNothingWhenAlreadyVerified() {
        register("mail.done", "done.mail@example.com");
        api.post(VERIFY, null, Map.of("token", emails.lastTokenFor("done.mail@example.com").orElseThrow()));
        emails.clear();

        String token = api.login("mail.done", TestDataFactory.PASSWORD);
        api.post("/api/v1/users/me/email/verify", token, null);

        assertThat(emails.sent()).isEmpty();
    }

    private org.springframework.test.web.servlet.MvcResult register(String username, String email) {
        return api.post(
                REGISTER,
                null,
                Map.of(
                        "username", username,
                        "email", email,
                        "password", TestDataFactory.PASSWORD,
                        "firstName", "Test",
                        "lastName", "Person"));
    }
}
