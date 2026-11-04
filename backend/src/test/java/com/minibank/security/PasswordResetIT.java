package com.minibank.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.support.TestDataFactory;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The forgotten-password flow.
 *
 * <p>Two things are being protected here, and both are asserted: that the flow cannot be used to
 * discover which numbers are registered, and that completing it removes whoever else was signed in.
 */
@DisplayName("Security centre — password reset")
class PasswordResetIT extends AbstractIntegrationTest {

    private static final String PHONE = "+999700000055";
    private static final String NEW_PASSWORD = "BrandNewPassword456!";

    @Autowired
    private UserRepository users;

    @BeforeEach
    @Transactional
    void createVerifiedUser() {
        User alice = data.customer("alice");
        alice.confirmPhoneNumber(PHONE, Instant.now());
        users.save(alice);
    }

    @Test
    @DisplayName("code, ticket, new password — and every device is signed out")
    void resetsThePasswordAndEndsEverySession() {
        String oldSessionRefresh = api.loginResponse("alice", TestDataFactory.PASSWORD)
                .get("refreshToken")
                .asText();

        JsonNode challenge =
                api.parse(api.post("/api/v1/security/password-reset/request", null, Map.of("phoneNumber", PHONE)));
        // The test profile opts in to seeing the code, so the flow can be completed without a handset.
        String code = challenge.get("developmentCode").asText();

        JsonNode ticket = api.parse(api.post(
                "/api/v1/security/password-reset/verify", null, Map.of("phoneNumber", PHONE, "code", code)));

        assertThat(api.post(
                                "/api/v1/security/password-reset/complete",
                                null,
                                Map.of(
                                        "phoneNumber", PHONE,
                                        "ticket", ticket.get("token").asText(),
                                        "newPassword", NEW_PASSWORD))
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);

        // The new password works and the old one does not.
        assertThat(api.attemptLogin("alice", NEW_PASSWORD)).isEqualTo(200);
        assertThat(api.attemptLogin("alice", TestDataFactory.PASSWORD)).isEqualTo(401);

        // Whoever was signed in before is signed out. If the reset happened because somebody else
        // had the account, leaving their token alive would defeat the entire exercise.
        assertThat(api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", oldSessionRefresh))
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("an unregistered number gets the same answer as a registered one")
    void doesNotRevealWhetherANumberIsRegistered() {
        JsonNode known =
                api.parse(api.post("/api/v1/security/password-reset/request", null, Map.of("phoneNumber", PHONE)));
        JsonNode unknown = api.parse(api.post(
                "/api/v1/security/password-reset/request", null, Map.of("phoneNumber", "+999700000099")));

        // Same shape, same fields. A reset form that says "no account with that number" is a free
        // account-enumeration oracle.
        assertThat(unknown.has("destination")).isTrue();
        assertThat(unknown.has("expiresAt")).isTrue();
        assertThat(unknown.get("resendAvailableInSeconds").asInt())
                .isEqualTo(known.get("resendAvailableInSeconds").asInt());
        // And nothing was sent, so no code comes back for the number nobody holds.
        assertThat(unknown.has("developmentCode")).isFalse();
    }

    @Test
    @DisplayName("a wrong code does not produce a ticket")
    void refusesAWrongCode() {
        api.post("/api/v1/security/password-reset/request", null, Map.of("phoneNumber", PHONE));

        var response = api.post(
                "/api/v1/security/password-reset/verify", null, Map.of("phoneNumber", PHONE, "code", "000000"));

        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.attemptLogin("alice", TestDataFactory.PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("a ticket works once")
    void theTicketIsSingleUse() {
        JsonNode challenge =
                api.parse(api.post("/api/v1/security/password-reset/request", null, Map.of("phoneNumber", PHONE)));
        JsonNode ticket = api.parse(api.post(
                "/api/v1/security/password-reset/verify",
                null,
                Map.of("phoneNumber", PHONE, "code", challenge.get("developmentCode").asText())));
        String token = ticket.get("token").asText();

        api.post(
                "/api/v1/security/password-reset/complete",
                null,
                Map.of("phoneNumber", PHONE, "ticket", token, "newPassword", NEW_PASSWORD));

        // Replaying the ticket must not set the password a second time.
        var replay = api.post(
                "/api/v1/security/password-reset/complete",
                null,
                Map.of("phoneNumber", PHONE, "ticket", token, "newPassword", "YetAnotherPassword789!"));

        assertThat(replay.getResponse().getStatus()).isEqualTo(403);
        assertThat(api.attemptLogin("alice", NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("a reset code cannot be used to sign in")
    void aResetCodeIsNotALoginCode() {
        JsonNode challenge =
                api.parse(api.post("/api/v1/security/password-reset/request", null, Map.of("phoneNumber", PHONE)));

        // The purpose is bound to the code when it is issued and checked again when it is redeemed.
        var loginAttempt = api.post(
                "/api/v1/auth/phone/login",
                null,
                Map.of("phoneNumber", PHONE, "code", challenge.get("developmentCode").asText()));

        assertThat(loginAttempt.getResponse().getStatus()).isEqualTo(400);
    }
}
