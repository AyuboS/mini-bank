package com.minibank.common.security;

import com.minibank.common.config.SecurityProperties;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.ErrorCode;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless access tokens.
 *
 * <p>Access tokens are short lived and are never revoked individually: revocation is handled by the
 * refresh token chain, which is stateful. That keeps the hot path (validating a request) free of
 * database access while still allowing a session to be terminated.
 */
// Final so that a constructor that can throw (an under-strength signing key is rejected there)
// cannot be subverted through a subclass with a finaliser.
@Service
public final class JwtService {

    /** Claim holding the user's role, so authorization needs no database round trip. */
    public static final String CLAIM_ROLE = "role";
    /** Claim holding the username, for logging and for populating the principal. */
    public static final String CLAIM_USERNAME = "username";
    /** Claim distinguishing token purposes, so a refresh token can never be used as a bearer token. */
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";

    private final SecretKey signingKey;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Mints a signed access token for the given user. */
    public IssuedToken issueAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        String token = Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Verifies a token's signature, issuer and expiry and rebuilds the principal from its claims.
     *
     * @throws ApiException with {@link ErrorCode#AUTH_TOKEN_EXPIRED} or {@link
     *     ErrorCode#AUTH_TOKEN_INVALID}
     */
    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.jwt().issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED, ErrorCode.AUTH_TOKEN_EXPIRED.defaultMessage(), ex);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, ErrorCode.AUTH_TOKEN_INVALID.defaultMessage(), ex);
        }

        if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Token is not an access token");
        }

        // A validly signed token can still carry claims this application does not understand, for
        // example after a rollback to an older claim format. Each one is checked explicitly rather
        // than by catching what a missing claim happens to throw.
        String subject = claims.getSubject();
        String roleClaim = claims.get(CLAIM_ROLE, String.class);
        String username = claims.get(CLAIM_USERNAME, String.class);
        if (subject == null || roleClaim == null || username == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Token is missing required claims");
        }

        try {
            return new AuthenticatedUser(UUID.fromString(subject), username, UserRole.valueOf(roleClaim), true);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Token claims are malformed", ex);
        }
    }

    public Instant accessTokenExpiry() {
        return clock.instant().plus(properties.jwt().accessTokenTtl());
    }

    /** A freshly minted token together with the moment it stops being valid. */
    public record IssuedToken(String value, Instant expiresAt) {}
}
