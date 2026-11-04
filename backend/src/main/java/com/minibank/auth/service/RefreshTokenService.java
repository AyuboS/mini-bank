package com.minibank.auth.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.auth.domain.RefreshToken;
import com.minibank.auth.session.DeviceFingerprint;
import com.minibank.auth.repository.RefreshTokenRepository;
import com.minibank.common.config.SecurityProperties;
import com.minibank.common.error.ApiException;
import com.minibank.common.error.ErrorCode;
import com.minibank.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>Rotation is mandatory: every successful refresh burns the presented token and returns a new
 * one. Combined with family-wide revocation on reuse, a stolen token is useful to an attacker only
 * until the legitimate client next refreshes — at which point one of the two presents a superseded
 * token, the theft is detected, and both are logged out.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;
    private final AuditService auditService;
    private final TokenFamilyRevoker tokenFamilyRevoker;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            SecurityProperties securityProperties,
            AuditService auditService,
            TokenFamilyRevoker tokenFamilyRevoker,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityProperties = securityProperties;
        this.auditService = auditService;
        this.tokenFamilyRevoker = tokenFamilyRevoker;
        this.clock = clock;
    }

    /** Starts a new token family for a fresh login. */
    @Transactional
    public IssuedRefreshToken issueNewFamily(User user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * Validates a presented refresh token and replaces it with a new one.
     *
     * @throws ApiException with {@link ErrorCode#AUTH_REFRESH_TOKEN_INVALID} if the token is
     *     unknown, expired, or already used
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashWithUser(hash(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));

        if (stored.isRevoked()) {
            // Presenting a token that was already rotated away means two parties hold it.
            // The safe assumption is theft: burn the entire chain.
            handleReuse(stored, now);
            throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
        if (stored.isExpired(now)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        User user = stored.getUser();
        if (!user.getStatus().canAuthenticate()) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE);
        }

        // The replacement inherits the original device tag: one login is one session, however many
        // times its token has been rotated since.
        IssuedRefreshToken replacement =
                issue(user, stored.getFamilyId(), stored.getDeviceLabel(), stored.getIpAddress());
        stored.revoke(now, RefreshToken.RevocationReason.ROTATED);
        stored.setReplacedByTokenHash(hash(replacement.value()));
        return new RotationResult(user, replacement);
    }

    /** Revokes the presented token and every other token in its family. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)).ifPresent(token -> {
            revokeFamily(token.getFamilyId(), RefreshToken.RevocationReason.LOGOUT);
            auditService.record(AuditEvent.success(AuditAction.LOGOUT).by(token.getUser()));
        });
    }

    /** Revokes every active session of a user, for example after a password change. */
    @Transactional
    public int revokeAllSessions(UUID userId, RefreshToken.RevocationReason reason) {
        Instant now = clock.instant();
        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        active.forEach(token -> token.revoke(now, reason));
        return active.size();
    }

    private IssuedRefreshToken issue(User user, UUID familyId) {
        return issue(user, familyId, DeviceFingerprint.currentDeviceLabel(), DeviceFingerprint.currentIpAddress());
    }

    /**
     * Issues a token, tagged with the device it was handed to.
     *
     * <p>The tag is carried across rotation rather than re-read each time, so a session keeps the
     * identity it was created with. Re-reading it would let a rotation from a different network
     * silently rewrite what the customer sees, which is exactly the signal they are looking at the
     * page to notice.
     */
    private IssuedRefreshToken issue(User user, UUID familyId, String deviceLabel, String ipAddress) {
        String rawToken = generateRawToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(securityProperties.jwt().refreshTokenTtl());
        RefreshToken entity =
                RefreshToken.issue(user, hash(rawToken), familyId, expiresAt, deviceLabel, ipAddress, now);
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(rawToken, expiresAt, familyId);
    }

    private void handleReuse(RefreshToken reused, Instant now) {
        log.warn(
                "Refresh token reuse detected for user {} in family {}; revoking family",
                reused.getUser().getId(),
                reused.getFamilyId());
        // Committed in a separate transaction: this method's caller is about to throw, and a
        // revocation that rolls back with it would leave the stolen family usable.
        tokenFamilyRevoker.revokeFamilyNow(reused.getFamilyId(), RefreshToken.RevocationReason.REUSE_DETECTED);
        auditService.recordIndependently(AuditEvent.failure(AuditAction.TOKEN_REUSE_DETECTED)
                .by(reused.getUser())
                .on("RefreshTokenFamily", reused.getFamilyId()));
    }

    private void revokeFamily(UUID familyId, RefreshToken.RevocationReason reason) {
        Instant now = clock.instant();
        refreshTokenRepository.findAllByFamilyIdAndRevokedAtIsNull(familyId)
                .forEach(token -> token.revoke(now, reason));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest of a token value. */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS; this branch is unreachable on any conformant JVM.
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /** Removes tokens that expired long enough ago that they are of no forensic value. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(clock.instant().minus(java.time.Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Purged {} expired refresh tokens", deleted);
        }
    }

    /** A raw refresh token together with its metadata. The raw value is never persisted. */
    public record IssuedRefreshToken(String value, Instant expiresAt, UUID familyId) {}

    /** The outcome of a successful rotation. */
    public record RotationResult(User user, IssuedRefreshToken refreshToken) {}
}
