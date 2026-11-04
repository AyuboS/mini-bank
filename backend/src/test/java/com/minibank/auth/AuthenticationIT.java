package com.minibank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minibank.support.AbstractIntegrationTest;
import com.minibank.support.TestDataFactory;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserStatus;
import com.minibank.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Authentication")
class AuthenticationIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private static Map<String, String> registration(String username, String email, String password) {
        return Map.of(
                "username", username,
                "email", email,
                "password", password,
                "firstName", "John",
                "lastName", "Smith");
    }

    @Test
    void registrationCreatesACustomerWithADefaultEuroAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("john", "john@example.com", "StrongPassword123!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String token = api.login("john", "StrongPassword123!");
        var accounts = api.getAsUser("/api/v1/accounts", token);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).get("currency").asText()).isEqualTo("EUR");
        assertThat(accounts.get(0).get("balance").decimalValue()).isEqualByComparingTo("0");
        assertThat(accounts.get(0).get("accountNumber").asText()).matches("^MB-\\d{12}$");
    }

    @Test
    void registrationNeverReturnsThePasswordHash() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("john", "john@example.com", "StrongPassword123!"))))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("passwordHash")
                .doesNotContain("StrongPassword123!")
                .doesNotContain("$2a$");
    }

    @Test
    void registrationNormalisesTheUsernameAndEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("JoHn", "John@Example.COM", "StrongPassword123!"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByUsername("john")).isPresent();
        assertThat(userRepository.findByEmail("john@example.com")).isPresent();
    }

    @Test
    void aDuplicateUsernameIsRejected() throws Exception {
        data.customer("john");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("john", "other@example.com", "StrongPassword123!"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void aDuplicateEmailIsRejected() throws Exception {
        data.customer("john");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("different", "john@example.com", "StrongPassword123!"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void aWeakPasswordIsRejectedWithAFieldLevelMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("john", "john@example.com", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void anInvalidEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registration("john", "not-an-email", "StrongPassword123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void aRegistrationCannotAskForTheAdminRole() throws Exception {
        // Unknown properties are rejected, so a privileged field cannot be smuggled in and ignored.
        String payload =
                """
                {"username":"sneaky","email":"s@example.com","password":"StrongPassword123!",
                 "firstName":"S","lastName":"S","role":"ADMIN"}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUsername("sneaky")).isEmpty();
    }

    @Test
    void loginReturnsATokenPairAndTheProfile() throws Exception {
        data.customer("john");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "john", "password", TestDataFactory.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("john"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void loginRecordsTheMomentOfAuthentication() {
        data.customer("john");

        api.loginAsTestUser("john");

        assertThat(userRepository.findByUsername("john").orElseThrow().getLastLoginAt())
                .isNotNull();
    }

    @Test
    void anUnknownUsernameAndAWrongPasswordAreIndistinguishable() {
        data.customer("john");

        assertThat(api.attemptLogin("john", "WrongPassword123!")).isEqualTo(401);
        assertThat(api.attemptLogin("nobody", "WrongPassword123!")).isEqualTo(401);
    }

    @Test
    void repeatedFailuresLockTheAccountAndTheCorrectPasswordThenStopsWorking() {
        // The test profile sets the threshold to three.
        data.customer("john");

        assertThat(api.attemptLogin("john", "Wrong-Password-1!")).isEqualTo(401);
        assertThat(api.attemptLogin("john", "Wrong-Password-2!")).isEqualTo(401);
        assertThat(api.attemptLogin("john", "Wrong-Password-3!")).isEqualTo(423);

        assertThat(api.attemptLogin("john", TestDataFactory.PASSWORD))
                .as("the right password is refused while the lockout holds")
                .isEqualTo(423);

        User locked = userRepository.findByUsername("john").orElseThrow();
        assertThat(locked.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(locked.getFailedLoginAttempts()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void aSuccessfulLoginClearsAPartialFailureStreak() {
        data.customer("john");

        api.attemptLogin("john", "Wrong-Password-1!");
        api.attemptLogin("john", "Wrong-Password-2!");
        api.loginAsTestUser("john");

        assertThat(userRepository.findByUsername("john").orElseThrow().getFailedLoginAttempts())
                .isZero();
    }

    @Test
    void aSuspendedUserCannotLogIn() {
        User user = data.customer("john");
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        assertThat(api.attemptLogin("john", TestDataFactory.PASSWORD)).isEqualTo(403);
    }

    @Test
    void refreshRotatesBothTokens() throws Exception {
        data.customer("john");
        var tokens = api.loginResponse("john", TestDataFactory.PASSWORD);
        String originalRefresh = tokens.get("refreshToken").asText();

        var refreshed = api.parse(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", originalRefresh))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(refreshed.get("refreshToken").asText())
                .as("the refresh token is replaced, not reissued")
                .isNotEqualTo(originalRefresh);
        assertThat(refreshed.get("accessToken").asText()).isNotEmpty();
    }

    @Test
    void aRotatedRefreshTokenCannotBeUsedAgain() throws Exception {
        data.customer("john");
        String originalRefresh =
                api.loginResponse("john", TestDataFactory.PASSWORD).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", originalRefresh))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", originalRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
    }

    @Test
    void replayingAnOldRefreshTokenBurnsTheWholeSessionFamily() throws Exception {
        // The theft scenario: an attacker replays a token the legitimate client already rotated.
        // Both parties must end up logged out, which means the revocation has to be committed even
        // though the request that detects it ends by throwing.
        data.customer("john");
        String first = api.loginResponse("john", TestDataFactory.PASSWORD)
                .get("refreshToken")
                .asText();

        var second = api.parse(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first))))
                .andReturn());
        String secondRefresh = second.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        data.customer("john");
        String refresh = api.loginResponse("john", TestDataFactory.PASSWORD)
                .get("refreshToken")
                .asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevealsNothingAboutWhetherATokenExisted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "a-token-that-never-existed"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void changingThePasswordRevokesEveryExistingSession() throws Exception {
        data.customer("john");
        var tokens = api.loginResponse("john", TestDataFactory.PASSWORD);
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", TestDataFactory.PASSWORD,
                                "newPassword", "BrandNewPassword456!"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());

        assertThat(api.attemptLogin("john", "BrandNewPassword456!")).isEqualTo(200);
        assertThat(api.attemptLogin("john", TestDataFactory.PASSWORD)).isEqualTo(401);
    }

    @Test
    void changingThePasswordRequiresTheCurrentOne() throws Exception {
        data.customer("john");
        String token = api.loginAsTestUser("john");

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", "NotTheRightOne1!",
                                "newPassword", "BrandNewPassword456!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void aClientSuppliedCorrelationIdIsEchoedBack() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header("X-Request-Id", "client-trace-42"))
                .andExpect(header().string("X-Request-Id", "client-trace-42"));
    }

    @Test
    void aCorrelationIdContainingNewlinesIsReplacedToPreventLogForging() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header("X-Request-Id", "evil\nINFO fake log line"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("evil\nINFO fake log line")));
    }
}
