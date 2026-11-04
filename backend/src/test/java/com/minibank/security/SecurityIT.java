package com.minibank.security;

import static com.minibank.support.TransferRequests.newKey;
import static com.minibank.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.config.SecurityProperties;
import com.minibank.common.security.JwtService;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Security")
class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private UserRepository userRepository;

    private User alice;
    private BankAccount aliceAccount;
    private BankAccount bobAccount;
    private String aliceToken;
    private String adminToken;

    @BeforeEach
    void openAccounts() {
        alice = data.customer("alice");
        User bob = data.customer("bob");
        data.admin("root");
        aliceAccount = data.fundedAccount(alice, "100.00");
        bobAccount = data.fundedAccount(bob, "0.00");
        aliceToken = api.loginAsTestUser("alice");
        adminToken = api.loginAsTestUser("root");
    }

    private static final List<String> PROTECTED_PATHS = List.of(
            "/api/v1/accounts",
            "/api/v1/transactions",
            "/api/v1/beneficiaries",
            "/api/v1/users/me",
            "/api/v1/admin/users",
            "/api/v1/admin/transfers",
            "/api/v1/admin/audit-logs");

    @Test
    void everyPrivateEndpointRefusesAnUnauthenticatedCaller() {
        for (String path : PROTECTED_PATHS) {
            assertThat(api.get(path, null).getResponse().getStatus())
                    .as("unauthenticated GET %s", path)
                    .isEqualTo(401);
        }
    }

    @Test
    void anUnauthenticatedErrorUsesTheSameBodyShapeAsEveryOtherError() {
        var body = api.parse(api.get("/api/v1/accounts", null));

        assertThat(body.get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/accounts");
        assertThat(body.get("requestId").asText()).isNotEmpty();
    }

    @Test
    void aCustomerIsRefusedEveryAdminEndpoint() {
        for (String path : List.of("/api/v1/admin/users", "/api/v1/admin/accounts", "/api/v1/admin/audit-logs")) {
            assertThat(api.get(path, aliceToken).getResponse().getStatus())
                    .as("customer GET %s", path)
                    .isEqualTo(403);
        }
    }

    @Test
    void aGarbageTokenIsRejected() {
        assertThat(api.get("/api/v1/accounts", "not.a.jwt").getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void anExpiredTokenIsRejected() {
        // Minted by a service whose clock is far enough in the past that the token has lapsed.
        JwtService pastService = new JwtService(
                securityProperties,
                Clock.fixed(java.time.Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC));
        String expired = pastService.issueAccessToken(alice).value();

        assertThat(api.get("/api/v1/accounts", expired).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void aTokenSignedWithTheWrongSecretIsRejected() {
        SecurityProperties foreign = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "an-attackers-own-secret-of-at-least-32-characters",
                        securityProperties.jwt().issuer(),
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)),
                securityProperties.lockout(),
                securityProperties.cors());
        String forged = new JwtService(foreign, Clock.systemUTC()).issueAccessToken(alice).value();

        assertThat(api.get("/api/v1/accounts", forged).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void aTokenClaimingTheAdminRoleButSignedWithTheWrongKeyIsRejected() {
        User forgedAdmin = data.customer("mallory");
        forgedAdmin.setRole(com.minibank.user.domain.UserRole.ADMIN);
        SecurityProperties foreign = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "another-attacker-secret-of-at-least-32-characters",
                        securityProperties.jwt().issuer(),
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)),
                securityProperties.lockout(),
                securityProperties.cors());
        String forged = new JwtService(foreign, Clock.systemUTC()).issueAccessToken(forgedAdmin).value();

        assertThat(api.get("/api/v1/admin/users", forged).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void aRefreshTokenCannotBeUsedAsABearerToken() {
        String refreshToken =
                api.loginResponse("alice", com.minibank.support.TestDataFactory.PASSWORD)
                        .get("refreshToken")
                        .asText();

        assertThat(api.get("/api/v1/accounts", refreshToken).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void oneCustomerCannotReachAnothersResources() {
        assertThat(api.get("/api/v1/accounts/" + bobAccount.getId(), aliceToken)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
        assertThat(api.get("/api/v1/accounts/" + bobAccount.getId() + "/transactions", aliceToken)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    @Test
    void aPasswordIsNeverReturnedByAnyEndpoint() throws Exception {
        for (String path : List.of("/api/v1/users/me", "/api/v1/accounts", "/api/v1/admin/users")) {
            String token = path.startsWith("/api/v1/admin") ? adminToken : aliceToken;
            String body = api.get(path, token).getResponse().getContentAsString();
            assertThat(body)
                    .as("body of %s", path)
                    .doesNotContain("passwordHash")
                    .doesNotContain("$2a$")
                    .doesNotContain(com.minibank.support.TestDataFactory.PASSWORD);
        }
    }

    @Test
    void anInternalErrorNeverLeaksAStackTraceOrClassNames() throws Exception {
        var body = api.get("/api/v1/accounts/not-a-uuid", aliceToken).getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("java.lang")
                .doesNotContain("org.springframework")
                .doesNotContain("com.minibank")
                .doesNotContain("Exception");
    }

    @Test
    void securityHeadersArePresentOnResponses() {
        var response = api.get("/api/v1/accounts", aliceToken).getResponse();

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
    }

    @Test
    void actuatorHealthIsPublicButMetricsAreNot() {
        assertThat(api.get("/actuator/health", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(api.get("/actuator/metrics", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(api.get("/actuator/metrics", aliceToken).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(api.get("/actuator/metrics", adminToken).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void businessMetricsAreRecorded() {
        api.post(
                "/api/v1/transfers",
                aliceToken,
                transfer(aliceAccount.getId(), bobAccount.getAccountNumber(), "10.00"),
                newKey());

        var metric = api.parse(api.get("/actuator/metrics/minibank.transfers", adminToken));

        assertThat(metric.get("name").asText()).isEqualTo("minibank.transfers");
        assertThat(metric.get("measurements").get(0).get("value").asDouble()).isPositive();
    }

    @Test
    void aSuspendedUsersExistingAccessTokenStopsWorkingOnceItExpires() {
        // The access token is stateless, so it stays parseable until it lapses. What must be
        // immediate is that no new session can be obtained, which the refresh path enforces.
        // Re-read first: logging in bumped the row's version, and saving the stale instance the
        // factory returned would fail the optimistic lock check rather than test anything.
        User current = userRepository.findById(alice.getId()).orElseThrow();
        current.setStatus(com.minibank.user.domain.UserStatus.SUSPENDED);
        userRepository.save(current);

        assertThat(api.attemptLogin("alice", com.minibank.support.TestDataFactory.PASSWORD))
                .isEqualTo(403);
    }

    @Test
    void aTransferBodyCannotSetFieldsItDoesNotOwn() {
        String payload =
                """
                {"fromAccountId":"%s","toAccountNumber":"%s","amount":10.00,"currency":"EUR",
                 "status":"COMPLETED","reference":"TRX-2026-FORGED1"}
                """
                        .formatted(aliceAccount.getId(), bobAccount.getAccountNumber());

        var result = api.post("/api/v1/transfers", aliceToken, payload, newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(databaseCleaner.balanceOf(aliceAccount.getAccountNumber())).isEqualByComparingTo("100.00");
    }

    @Test
    void anAccountNumberContainingSqlIsTreatedAsData() {
        var result = api.post(
                "/api/v1/transfers",
                aliceToken,
                Map.of(
                        "fromAccountId", aliceAccount.getId().toString(),
                        "toAccountNumber", "MB-1'; DROP TABLE transfers;--",
                        "amount", "10.00",
                        "currency", "EUR"),
                newKey());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(databaseCleaner.countRows("transfers")).as("the table is still there").isNotNegative();
    }
}
