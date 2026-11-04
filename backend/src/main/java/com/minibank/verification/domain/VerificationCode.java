package com.minibank.verification.domain;

import com.minibank.common.persistence.AuditedEntity;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One verification in progress, for either channel.
 *
 * <p>A single entity rather than a {@code PhoneVerification} and an {@code EmailVerificationToken}
 * that would differ only in the length of a string. Everything that actually matters here —
 * expiry, attempt counting, resend cooldown, single use — is identical for both, and duplicating it
 * would mean fixing every future bug twice.
 *
 * <p>The secret is never stored. {@code secretHash} is a keyed HMAC, not a bare digest: six digits
 * is a space of a million, which a plain SHA-256 rainbow table covers instantly, and the server key
 * is the only thing that makes the stored value useless on its own.
 */
@Entity
@Table(name = "verification_codes")
@Getter
@Setter
public class VerificationCode extends AuditedEntity {

    /** How long the ticket handed out after a successful check stays usable. */
    public static final Duration TICKET_TTL = Duration.ofMinutes(15);

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private VerificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private VerificationPurpose purpose;

    /** The normalised destination: an E.164 phone number, or a lower-cased email address. */
    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    /** Set when the verification belongs to an existing account; null before registration. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /**
     * Proof that this destination was verified, handed to the client after a successful check and
     * presented again when the account is finally created.
     *
     * <p>Without it, registration would have to trust that a row marked verified belongs to whoever
     * is calling: anybody who knew a number had just been confirmed could register against it.
     */
    @Column(name = "ticket_hash", length = 64)
    private String ticketHash;

    @Column(name = "ticket_expires_at")
    private Instant ticketExpiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected VerificationCode() {
        // for JPA
    }

    public static VerificationCode start(
            VerificationChannel channel,
            VerificationPurpose purpose,
            String destination,
            User user,
            String secretHash,
            int maxAttempts,
            Duration ttl,
            Instant now) {

        VerificationCode code = new VerificationCode();
        code.channel = channel;
        code.purpose = purpose;
        code.destination = destination;
        code.user = user;
        code.secretHash = secretHash;
        code.maxAttempts = maxAttempts;
        code.expiresAt = now.plus(ttl);
        code.lastSentAt = now;
        code.attempts = 0;
        return code;
    }

    /** Replaces the secret on an existing row, which is what a resend is. */
    public void reissue(String newSecretHash, Duration ttl, Instant now) {
        this.secretHash = newSecretHash;
        this.expiresAt = now.plus(ttl);
        this.lastSentAt = now;
        this.attempts = 0;
        this.consumedAt = null;
        this.ticketHash = null;
        this.ticketExpiresAt = null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    /** True while the caller must wait before another message may be sent. */
    public boolean isWithinResendCooldown(Duration cooldown, Instant now) {
        return lastSentAt.plus(cooldown).isAfter(now);
    }

    /** Seconds remaining before a resend is allowed; zero when one may be sent now. */
    public long resendAvailableIn(Duration cooldown, Instant now) {
        long seconds = Duration.between(now, lastSentAt.plus(cooldown)).toSeconds();
        return Math.max(seconds, 0);
    }

    public void registerFailedAttempt() {
        attempts++;
    }

    /**
     * Marks the code used and issues the ticket.
     *
     * <p>Consumption and issuing happen together deliberately: a code that has been checked
     * successfully must stop working at that instant, whether or not the caller goes on to use the
     * ticket.
     */
    public void consume(String newTicketHash, Instant now) {
        this.consumedAt = now;
        this.ticketHash = newTicketHash;
        // The deadline exists only if there is a ticket to put one on. An email link is consumed
        // without issuing anything, and a lone expiry would be a row the check constraint rejects.
        this.ticketExpiresAt = newTicketHash == null ? null : now.plus(TICKET_TTL);
    }

    public boolean isTicketUsable(Instant now) {
        return ticketHash != null && ticketExpiresAt != null && ticketExpiresAt.isAfter(now);
    }

    /** Burns the ticket once the account it authorised has been created. */
    public void redeemTicket() {
        this.ticketHash = null;
        this.ticketExpiresAt = null;
    }
}
