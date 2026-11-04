package com.minibank.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.minibank.support.AbstractIntegrationTest;
import com.minibank.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sessions and sign-in history.
 *
 * <p>The property that matters is that revocation is server-side. Every assertion about a session
 * ending is followed by an attempt to actually use its refresh token, because a session list that
 * hides a row without invalidating the token would look identical and protect nobody.
 */
@DisplayName("Security centre — sessions")
class SessionIT extends AbstractIntegrationTest {

    @BeforeEach
    void createUser() {
        data.customer("alice");
    }

    @Test
    @DisplayName("each login is one session, however many times its token has rotated")
    void eachLoginIsOneSession() {
        JsonNode first = api.loginResponse("alice", TestDataFactory.PASSWORD);
        api.loginResponse("alice", TestDataFactory.PASSWORD);

        JsonNode sessions = api.getAsUser("/api/v1/security/sessions", first.get("accessToken").asText());
        assertThat(sessions).hasSize(2);

        // Rotating the first session's token must not add a third row.
        api.post("/api/v1/auth/refresh", null, java.util.Map.of("refreshToken", first.get("refreshToken").asText()));
        assertThat(api.getAsUser("/api/v1/security/sessions", first.get("accessToken").asText()))
                .hasSize(2);
    }

    @Test
    @DisplayName("the caller's own session is marked when they say which one it is")
    void marksTheCurrentSession() {
        JsonNode login = api.loginResponse("alice", TestDataFactory.PASSWORD);
        String refresh = login.get("refreshToken").asText();

        JsonNode sessions = api.getAsUser(
                "/api/v1/security/sessions?currentRefreshToken=" + refresh, login.get("accessToken").asText());

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).get("current").asBoolean()).isTrue();
        // No token, hash or fragment of one is ever returned.
        assertThat(sessions.toString()).doesNotContain(refresh);
    }

    @Test
    @DisplayName("ending a session actually invalidates its refresh token")
    void endingASessionRevokesItServerSide() {
        JsonNode victim = api.loginResponse("alice", TestDataFactory.PASSWORD);
        JsonNode survivor = api.loginResponse("alice", TestDataFactory.PASSWORD);

        JsonNode sessions = api.getAsUser(
                "/api/v1/security/sessions?currentRefreshToken=" + survivor.get("refreshToken").asText(),
                survivor.get("accessToken").asText());
        String victimSessionId = null;
        for (JsonNode session : sessions) {
            if (!session.get("current").asBoolean()) {
                victimSessionId = session.get("id").asText();
            }
        }
        assertThat(victimSessionId).isNotNull();

        assertThat(api.delete("/api/v1/security/sessions/" + victimSessionId, survivor.get("accessToken").asText())
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);

        // The revoked session's refresh token no longer works. This is the assertion that matters:
        // a list that merely hid the row would pass everything above and protect nobody.
        assertThat(api.post(
                                "/api/v1/auth/refresh",
                                null,
                                java.util.Map.of("refreshToken", victim.get("refreshToken").asText()))
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);

        // And the surviving session is untouched.
        assertThat(api.post(
                                "/api/v1/auth/refresh",
                                null,
                                java.util.Map.of("refreshToken", survivor.get("refreshToken").asText()))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("signing out other devices keeps the one making the request")
    void revokingOthersKeepsTheCaller() {
        JsonNode other = api.loginResponse("alice", TestDataFactory.PASSWORD);
        JsonNode mine = api.loginResponse("alice", TestDataFactory.PASSWORD);
        String myRefresh = mine.get("refreshToken").asText();

        JsonNode result = api.parse(api.post(
                "/api/v1/security/sessions/revoke-others?currentRefreshToken=" + myRefresh,
                mine.get("accessToken").asText(),
                null));
        assertThat(result.get("sessionsEnded").asInt()).isEqualTo(1);

        assertThat(api.post("/api/v1/auth/refresh", null, java.util.Map.of("refreshToken", other.get("refreshToken").asText()))
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
        // The customer who has just removed an unfamiliar device is not logged out themselves.
        assertThat(api.post("/api/v1/auth/refresh", null, java.util.Map.of("refreshToken", myRefresh))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("one customer cannot end another's session")
    void sessionsAreNotReachableAcrossCustomers() {
        JsonNode aliceLogin = api.loginResponse("alice", TestDataFactory.PASSWORD);
        String aliceSessionId = api.getAsUser("/api/v1/security/sessions", aliceLogin.get("accessToken").asText())
                .get(0)
                .get("id")
                .asText();

        data.customer("mallory");
        String malloryToken = api.loginAsTestUser("mallory");

        assertThat(api.delete("/api/v1/security/sessions/" + aliceSessionId, malloryToken)
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
        // Alice's session still works.
        assertThat(api.post(
                                "/api/v1/auth/refresh",
                                null,
                                java.util.Map.of("refreshToken", aliceLogin.get("refreshToken").asText()))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("sign-in history records both successes and failures")
    void loginHistoryShowsBothOutcomes() {
        api.attemptLogin("alice", "WrongPassword123!");
        String token = api.loginAsTestUser("alice");

        JsonNode history = api.getAsUser("/api/v1/security/login-history", token);

        assertThat(history).isNotEmpty();
        assertThat(history.findValuesAsText("outcome")).contains("SUCCESS");
        assertThat(history.findValuesAsText("outcome")).contains("FAILURE");
    }
}
