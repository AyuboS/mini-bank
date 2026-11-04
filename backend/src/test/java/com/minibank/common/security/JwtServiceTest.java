package com.minibank.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minibank.common.config.SecurityProperties;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.ErrorCode;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-value-at-least-32-chars-long";
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    private User user;

    private static SecurityProperties properties(String secret, String issuer) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(7)),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new SecurityProperties.Cors(List.of("http://localhost:5173")));
    }

    private static JwtService serviceAt(Instant instant, String secret, String issuer) {
        return new JwtService(properties(secret, issuer), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        user = User.create("john", "john@example.com", "hash", "John", "Smith", UserRole.CUSTOMER);
        user.setId(UUID.randomUUID());
    }

    @Test
    void issuesATokenThatParsesBackToTheSamePrincipal() {
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");

        JwtService.IssuedToken issued = service.issueAccessToken(user);
        AuthenticatedUser parsed = service.parseAccessToken(issued.value());

        assertThat(parsed.getId()).isEqualTo(user.getId());
        assertThat(parsed.getUsername()).isEqualTo("john");
        assertThat(parsed.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void reportsWhenTheTokenExpires() {
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");

        assertThat(service.issueAccessToken(user).expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void carriesTheRoleSoAuthorizationNeedsNoDatabaseRead() {
        user.setRole(UserRole.ADMIN);
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");

        assertThat(service.parseAccessToken(service.issueAccessToken(user).value()).isAdmin())
                .isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = serviceAt(NOW, SECRET, "mini-bank").issueAccessToken(user).value();
        JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(16)), SECRET, "mini-bank");

        assertThatThrownBy(() -> later.parseAccessToken(token))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    void acceptsATokenThatHasNotQuiteExpired() {
        String token = serviceAt(NOW, SECRET, "mini-bank").issueAccessToken(user).value();
        JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(14)), SECRET, "mini-bank");

        assertThat(later.parseAccessToken(token).getId()).isEqualTo(user.getId());
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String forged = serviceAt(NOW, "an-entirely-different-secret-32-chars-plus", "mini-bank")
                .issueAccessToken(user)
                .value();
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");

        assertThatThrownBy(() -> service.parseAccessToken(forged))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void rejectsATokenWhosePayloadHasBeenTamperedWith() {
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");
        String token = service.issueAccessToken(user).value();
        String[] parts = token.split("\\.");
        String tamperedPayload = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
                                .replace("CUSTOMER", "ADMIN___")
                                .getBytes());
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> service.parseAccessToken(tampered))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        String foreign = serviceAt(NOW, SECRET, "somebody-elses-bank").issueAccessToken(user).value();
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");

        assertThatThrownBy(() -> service.parseAccessToken(foreign)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsGarbage() {
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");

        assertThatThrownBy(() -> service.parseAccessToken("not.a.jwt")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.parseAccessToken("")).isInstanceOf(ApiException.class);
    }

    @Test
    void issuesADistinctTokenIdEachTime() {
        JwtService service = serviceAt(NOW, SECRET, "mini-bank");

        assertThat(service.issueAccessToken(user).value())
                .isNotEqualTo(service.issueAccessToken(user).value());
    }
}
