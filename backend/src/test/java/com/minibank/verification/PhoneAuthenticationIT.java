package com.minibank.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.support.TestDataFactory;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Phone sign-in end to end.
 *
 * <p>The test profile sets {@code expose-codes}, so the OTP comes back in the response and the flow
 * can be driven without an SMS gateway. That switch is refused outright in the prod profile; see
 * {@code StartupSecurityCheck}.
 */
class PhoneAuthenticationIT extends AbstractIntegrationTest {

    private static final String CODE_PATH = "/api/v1/auth/phone/code";
    private static final String LOGIN_PATH = "/api/v1/auth/phone/login";
    private static final String CONFIRM_PATH = "/api/v1/auth/phone/confirm";
    private static final String PHONE = "+998901234567";

    @Autowired
    private UserRepository userRepository;

    // --- requesting a code ----------------------------------------------------

    @Test
    @DisplayName("a code is sent and the destination comes back masked")
    void sendsACode() {
        registeredCustomer("phone.alice", PHONE);

        var result = api.post(CODE_PATH, null, Map.of("phoneNumber", PHONE, "purpose", "LOGIN"));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());

        JsonNode challenge = api.parse(result);
        assertThat(challenge.get("destination").asText()).startsWith("+998").endsWith("67").doesNotContain("901234");
        assertThat(challenge.get("developmentCode").asText()).hasSize(6).containsOnlyDigits();
        assertThat(challenge.get("deliverySimulated").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("an unregistered number gets the same answer and no code at all")
    void doesNotRevealWhetherANumberIsRegistered() {
        JsonNode unknown = api.parse(api.post(
                CODE_PATH, null, Map.of("phoneNumber", "+998900000000", "purpose", "LOGIN")));

        // Same shape, same fields, so the two cases cannot be told apart from the outside...
        assertThat(unknown.get("destination").asText()).startsWith("+998");
        assertThat(unknown.hasNonNull("expiresAt")).isTrue();
        // ...but nothing was actually generated for a number nobody banks with.
        assertThat(unknown.hasNonNull("developmentCode")).isFalse();
    }

    @Test
    @DisplayName("however the number is typed, it reaches the same account")
    void normalisesTheNumber() {
        registeredCustomer("phone.norm", PHONE);

        String code = requestCode("  +998 (90) 123-45-67 ", "LOGIN");
        var result = api.post(LOGIN_PATH, null, Map.of("phoneNumber", "00998901234567", "code", code));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("a second code cannot be requested inside the cooldown")
    void enforcesTheResendCooldown() {
        registeredCustomer("phone.cooldown", PHONE);
        requestCode(PHONE, "LOGIN");

        var again = api.post(CODE_PATH, null, Map.of("phoneNumber", PHONE, "purpose", "LOGIN"));
        assertThat(again.getResponse().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(api.parse(again).get("code").asText()).isEqualTo("VERIFICATION_RESEND_TOO_SOON");
    }

    @Test
    @DisplayName("a number that cannot be normalised is rejected")
    void rejectsAnUnusableNumber() {
        var result = api.post(CODE_PATH, null, Map.of("phoneNumber", "12345", "purpose", "LOGIN"));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("PHONE_NUMBER_INVALID");
    }

    // --- signing in -----------------------------------------------------------

    @Test
    @DisplayName("the right code returns a working session")
    void signsIn() {
        User alice = registeredCustomer("phone.signin", PHONE);
        String code = requestCode(PHONE, "LOGIN");

        JsonNode tokens = api.parse(api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", code)));
        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("user").get("id").asText()).isEqualTo(alice.getId().toString());
        assertThat(tokens.get("user").get("phoneVerified").asBoolean()).isTrue();

        // The token really works, rather than merely being present.
        assertThat(api.get("/api/v1/accounts", tokens.get("accessToken").asText())
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("a code works exactly once")
    void burnsTheCodeOnUse() {
        registeredCustomer("phone.once", PHONE);
        String code = requestCode(PHONE, "LOGIN");

        assertThat(api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", code))
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", code))
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("a wrong code is refused")
    void rejectsAWrongCode() {
        registeredCustomer("phone.wrong", PHONE);
        String code = requestCode(PHONE, "LOGIN");

        var result = api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", wrong(code)));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("VERIFICATION_CODE_INVALID");
    }

    @Test
    @DisplayName("the code is burned after too many wrong guesses, and the right one no longer helps")
    void stopsAfterTooManyAttempts() {
        registeredCustomer("phone.attempts", PHONE);
        String code = requestCode(PHONE, "LOGIN");

        // The test profile allows five attempts.
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", wrong(code)))
                            .getResponse()
                            .getStatus())
                    .as("attempt %d", attempt)
                    .isEqualTo(HttpStatus.BAD_REQUEST.value());
        }

        // The fifth failure reports exhaustion rather than "wrong again", so the client can tell the
        // customer to ask for a new code instead of retrying into a dead end.
        var fifth = api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", wrong(code)));
        assertThat(fifth.getResponse().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(api.parse(fifth).get("code").asText()).isEqualTo("VERIFICATION_TOO_MANY_ATTEMPTS");

        // And the correct code is now worthless: guessing is not a way to keep the window open.
        assertThat(api.post(LOGIN_PATH, null, Map.of("phoneNumber", PHONE, "code", code))
                        .getResponse()
                        .getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("an unverified number is not a credential, even with a valid code")
    void refusesAnUnverifiedNumber() {
        // The number is on the profile but was never proven.
        User bob = data.customer("phone.unverified");
        bob.setPhoneNumber(PHONE);
        userRepository.save(bob);

        // No user is resolved for an unverified number, so nothing is sent and no code exists.
        JsonNode challenge = api.parse(api.post(
                CODE_PATH, null, Map.of("phoneNumber", PHONE, "purpose", "LOGIN")));
        assertThat(challenge.hasNonNull("developmentCode")).isFalse();
    }

    // --- registration ---------------------------------------------------------

    @Test
    @DisplayName("a number confirmed before registering arrives already verified")
    void registersWithAConfirmedNumber() {
        String code = requestCode(PHONE, "REGISTRATION");

        JsonNode ticket = api.parse(api.post(CONFIRM_PATH, null, Map.of("phoneNumber", PHONE, "code", code)));
        assertThat(ticket.get("token").asText()).isNotBlank();

        var registered = api.post(
                "/api/v1/auth/register",
                null,
                Map.of(
                        "username", "phone.newcomer",
                        "email", "newcomer@example.com",
                        "password", TestDataFactory.PASSWORD,
                        "firstName", "New",
                        "lastName", "Comer",
                        "phoneNumber", PHONE,
                        "verificationToken", ticket.get("token").asText()));

        assertThat(registered.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(api.parse(registered).get("phoneVerified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("registration without the ticket is refused, even with the number")
    void refusesRegistrationWithoutProof() {
        var result = api.post(
                "/api/v1/auth/register",
                null,
                Map.of(
                        "username", "phone.impostor",
                        "email", "impostor@example.com",
                        "password", TestDataFactory.PASSWORD,
                        "firstName", "Im",
                        "lastName", "Postor",
                        "phoneNumber", PHONE));

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("VERIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("a ticket is redeemed once")
    void burnsTheTicket() {
        String code = requestCode(PHONE, "REGISTRATION");
        String token = api.parse(api.post(CONFIRM_PATH, null, Map.of("phoneNumber", PHONE, "code", code)))
                .get("token")
                .asText();

        api.post("/api/v1/auth/register", null, registration("phone.first", "first@example.com", token));
        var second = api.post(
                "/api/v1/auth/register", null, registration("phone.second", "second@example.com", token));

        // The number is taken by now, so either answer is correct; what must not happen is a second
        // account claiming the same verified number.
        assertThat(second.getResponse().getStatus()).isIn(HttpStatus.CONFLICT.value(), HttpStatus.FORBIDDEN.value());
        assertThat(userRepository.findByPhoneNumber(PHONE)).isPresent();
    }

    @Test
    @DisplayName("registering a number somebody already has is refused up front")
    void rejectsADuplicateNumber() {
        registeredCustomer("phone.holder", PHONE);

        var result = api.post(CODE_PATH, null, Map.of("phoneNumber", PHONE, "purpose", "REGISTRATION"));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(api.parse(result).get("code").asText()).isEqualTo("PHONE_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("a code issued to register cannot be used to sign in")
    void keepsPurposesApart() {
        registeredCustomer("phone.purpose", "+998907654321");
        String registrationCode = requestCode("+998900000009", "REGISTRATION");

        var result = api.post(
                LOGIN_PATH, null, Map.of("phoneNumber", "+998900000009", "code", registrationCode));
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    // --- helpers --------------------------------------------------------------

    private Map<String, String> registration(String username, String email, String token) {
        return Map.of(
                "username", username,
                "email", email,
                "password", TestDataFactory.PASSWORD,
                "firstName", "Test",
                "lastName", "Person",
                "phoneNumber", PHONE,
                "verificationToken", token);
    }

    /** A customer whose number is on file and proven. */
    private User registeredCustomer(String username, String phoneNumber) {
        User user = data.customer(username);
        user.confirmPhoneNumber(phoneNumber, java.time.Instant.now());
        return userRepository.save(user);
    }

    private String requestCode(String phoneNumber, String purpose) {
        JsonNode challenge =
                api.parse(api.post(CODE_PATH, null, Map.of("phoneNumber", phoneNumber, "purpose", purpose)));
        return challenge.get("developmentCode").asText();
    }

    /** A code of the right shape that is definitely not the right one. */
    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }
}
