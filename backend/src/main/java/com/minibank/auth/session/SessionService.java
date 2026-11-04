package com.minibank.auth.session;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditLog;
import com.minibank.audit.repository.AuditLogRepository;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.auth.domain.RefreshToken;
import com.minibank.auth.repository.RefreshTokenRepository;
import com.minibank.auth.service.RefreshTokenService;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The security page's view of a customer's sessions and sign-in history.
 *
 * <h2>Sessions are families, not tokens</h2>
 *
 * <p>One login produces a token family whose members are rotated continuously. The customer thinks
 * in logins — "my laptop", "that phone" — so this class collapses each family to a single row and
 * ends a session by revoking the whole family. Ending one token instead would leave the session
 * alive on the next refresh, which is the worst possible outcome for a control whose entire purpose
 * is to lock somebody out.
 *
 * <h2>Server-side, always</h2>
 *
 * <p>Revocation writes to the database. There is no client-side session state to clear and nothing
 * a compromised client can do to keep a revoked family alive: the next refresh fails at the store.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** How far back the security page looks. Enough to spot a pattern, short enough to read. */
    private static final int HISTORY_SIZE = 25;

    /** The events that count as "somebody tried to get in". */
    private static final List<AuditAction> AUTHENTICATION_ACTIONS = List.of(
            AuditAction.LOGIN_SUCCESS,
            AuditAction.LOGIN_FAILED,
            AuditAction.PASSWORD_LOGIN,
            AuditAction.PHONE_LOGIN,
            AuditAction.LOGOUT,
            AuditAction.ACCOUNT_LOCKED,
            AuditAction.TOKEN_REUSE_DETECTED,
            AuditAction.PASSWORD_CHANGED);

    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository auditLogs;
    private final AuditService auditService;
    private final Clock clock;

    public SessionService(
            RefreshTokenRepository refreshTokens,
            AuditLogRepository auditLogs,
            AuditService auditService,
            Clock clock) {
        this.refreshTokens = refreshTokens;
        this.auditLogs = auditLogs;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * The customer's live sessions, newest activity first.
     *
     * @param currentRefreshToken the raw token the caller holds, if their client sent it, so the
     *     session they are using can be marked. Optional: the list is correct without it, it simply
     *     cannot say which row is "this device".
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(UUID userId, String currentRefreshToken) {
        Instant now = clock.instant();
        UUID currentFamily = familyOf(currentRefreshToken);

        // One row per family, keeping the most recently used token as the representative. A
        // LinkedHashMap preserves the query's ordering, so the list stays newest-first.
        Map<UUID, RefreshToken> byFamily = new LinkedHashMap<>();
        for (RefreshToken token : refreshTokens.findLiveSessions(userId, now)) {
            byFamily.merge(token.getFamilyId(), token, SessionService::mostRecent);
        }

        return byFamily.values().stream()
                .map(token -> new SessionResponse(
                        token.getFamilyId(),
                        token.getDeviceLabel() == null ? "Unknown device" : token.getDeviceLabel(),
                        token.getIpAddress(),
                        token.getFamilyId().equals(currentFamily),
                        token.getLastUsedAt() == null ? token.getCreatedAt() : token.getLastUsedAt(),
                        token.getCreatedAt(),
                        token.getExpiresAt()))
                .toList();
    }

    /** Ends one session by revoking every live token in its family. */
    @Transactional
    public void revokeSession(UUID familyId, AuthenticatedUser caller) {
        List<RefreshToken> live = refreshTokens.findLiveInFamily(caller.getId(), familyId);
        if (live.isEmpty()) {
            // Scoped by the caller's own id above, so another customer's family is simply not
            // found. The endpoint cannot be used to end somebody else's session or to probe for one.
            throw new NotFoundException(ErrorCode.SESSION_NOT_FOUND);
        }
        Instant now = clock.instant();
        live.forEach(token -> token.revoke(now, RefreshToken.RevocationReason.REVOKED_BY_SYSTEM));

        auditService.record(AuditEvent.success(AuditAction.SESSION_REVOKED)
                .by(caller.getId(), caller.getUsername())
                .on("Session", familyId));
        log.info("Session {} ended by its owner", familyId);
    }

    /**
     * Ends every session except the one making the request.
     *
     * <p>Keeping the caller signed in is the point: a customer who has just seen an unfamiliar
     * device wants to remove it, not to be logged out themselves while doing so.
     *
     * @return how many sessions were ended
     */
    @Transactional
    public int revokeOtherSessions(AuthenticatedUser caller, String currentRefreshToken) {
        UUID keep = familyOf(currentRefreshToken);
        Instant now = clock.instant();

        List<RefreshToken> live = refreshTokens.findAllByUserIdAndRevokedAtIsNull(caller.getId());
        long families = live.stream()
                .filter(token -> !token.getFamilyId().equals(keep))
                .peek(token -> token.revoke(now, RefreshToken.RevocationReason.REVOKED_BY_SYSTEM))
                .map(RefreshToken::getFamilyId)
                .distinct()
                .count();

        auditService.record(AuditEvent.success(AuditAction.ALL_SESSIONS_REVOKED)
                .by(caller.getId(), caller.getUsername())
                .with("sessionsEnded", families));
        log.info("User {} ended {} other sessions", caller.getId(), families);
        return (int) families;
    }

    /** Recent sign-in activity, read straight from the audit trail. */
    @Transactional(readOnly = true)
    public List<LoginHistoryEntry> loginHistory(UUID userId) {
        return auditLogs
                .findAuthenticationHistory(userId, AUTHENTICATION_ACTIONS, PageRequest.of(0, HISTORY_SIZE))
                .stream()
                .map(SessionService::toHistoryEntry)
                .toList();
    }

    private static LoginHistoryEntry toHistoryEntry(AuditLog entry) {
        return new LoginHistoryEntry(
                entry.getAction().name(),
                entry.getOutcome().name(),
                DeviceFingerprint.describe(entry.getUserAgent()),
                entry.getIpAddress(),
                entry.getCreatedAt());
    }

    /** The family a raw refresh token belongs to, or null when there is none to read. */
    private UUID familyOf(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }
        return refreshTokens
                .findByTokenHashWithUser(RefreshTokenService.hash(rawRefreshToken))
                .map(RefreshToken::getFamilyId)
                .orElse(null);
    }

    private static RefreshToken mostRecent(RefreshToken left, RefreshToken right) {
        return Comparator.comparing(
                        (RefreshToken token) -> token.getLastUsedAt() == null
                                ? token.getCreatedAt()
                                : token.getLastUsedAt())
                .compare(left, right)
                >= 0
                ? left
                : right;
    }
}
