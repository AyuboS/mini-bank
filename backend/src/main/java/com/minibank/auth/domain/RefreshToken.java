package com.minibank.auth.domain;

import com.minibank.common.persistence.BaseEntity;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A stored refresh token.
 *
 * <p>Only the SHA-256 hash of the token is persisted. The raw value exists exactly once, in the
 * response that hands it to the client; a dump of this table therefore yields nothing an attacker
 * can present back to the API. SHA-256 rather than BCrypt is the right choice here because the
 * token is 256 bits of cryptographic randomness — there is no low-entropy secret to slow-hash, and
 * lookup must be a single indexed read.
 *
 * <p>{@code familyId} links every token derived from one login. If a token that has already been
 * rotated away is presented again, that is evidence of theft, and the entire family is revoked.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 40)
    private String revokedReason;

    @Column(name = "replaced_by_token_hash", length = 64)
    private String replacedByTokenHash;

    /*
     * Session metadata.
     *
     * These are what turn a row of hashes into something a customer can act on: without them the
     * security page shows five identical entries and no way to tell the office laptop from the
     * phone that was stolen. The label is a reduced user agent, never the raw header -- see
     * DeviceFingerprint for why.
     */
    @Column(name = "device_label", length = 80)
    private String deviceLabel;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshToken() {
        // for JPA
    }

    public static RefreshToken issue(User user, String tokenHash, UUID familyId, Instant expiresAt) {
        return issue(user, tokenHash, familyId, expiresAt, null, null, null);
    }

    public static RefreshToken issue(
            User user,
            String tokenHash,
            UUID familyId,
            Instant expiresAt,
            String deviceLabel,
            String ipAddress,
            Instant issuedAt) {
        RefreshToken token = new RefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.familyId = familyId;
        token.expiresAt = expiresAt;
        token.deviceLabel = deviceLabel;
        token.ipAddress = ipAddress;
        token.lastUsedAt = issuedAt;
        return token;
    }

    /** Records that this token was just presented. Drives "active now" on the security page. */
    public void touch(Instant now) {
        lastUsedAt = now;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public void revoke(Instant now, RevocationReason reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason.name();
        }
    }

    /** Why a refresh token stopped being valid. Recorded for forensics. */
    public enum RevocationReason {
        /** Superseded by a newer token during normal rotation. */
        ROTATED,
        /** The user logged out. */
        LOGOUT,
        /** A superseded token was presented again, so the whole family was burned. */
        REUSE_DETECTED,
        /** An administrator or a password change invalidated the session. */
        REVOKED_BY_SYSTEM
    }
}
