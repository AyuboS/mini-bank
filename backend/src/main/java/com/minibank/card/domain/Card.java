package com.minibank.card.domain;

import com.minibank.account.domain.BankAccount;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A payment card attached to one bank account.
 *
 * <p>What is <em>not</em> here is the point of the class. There is no CVV column, in any form: the
 * verification value is derived on demand from a server-side key and never persisted (see {@code
 * CardCryptoService}), which mirrors how an issuer derives CVV from a card verification key rather
 * than storing it. The full number is held only as ciphertext, and every ordinary read path uses
 * {@link #getLastFour()} instead of decrypting anything.
 *
 * <p>{@code panFingerprint} is a keyed hash of the number, kept so that uniqueness can be enforced
 * without the plaintext ever being compared.
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
public class Card extends AuditedEntity {

    /** How long a newly issued card is valid. Four years is the common issuing period. */
    private static final int VALIDITY_YEARS = 4;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    /** What the cardholder calls it. Renameable, and shown in place of the number in lists. */
    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType = CardType.VIRTUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CardStatus status = CardStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "design", nullable = false, length = 20)
    private CardDesign design = CardDesign.AURORA;

    @Enumerated(EnumType.STRING)
    @Column(name = "network", nullable = false, length = 20)
    private CardNetwork network = CardNetwork.VISA;

    /** The issuer identification number, safe to show: it identifies the scheme, not the card. */
    @Column(name = "bin", nullable = false, length = 6)
    private String bin;

    @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    /** AES-GCM ciphertext of the full number. Decrypted only by an explicit, audited reveal. */
    @Column(name = "pan_ciphertext", nullable = false, length = 512)
    private String panCiphertext;

    /** HMAC of the full number, for uniqueness checks that never touch the plaintext. */
    @Column(name = "pan_fingerprint", nullable = false, length = 64)
    private String panFingerprint;

    @Column(name = "cardholder_name", nullable = false, length = 120)
    private String cardholderName;

    @Column(name = "expiration_month", nullable = false)
    private int expirationMonth;

    @Column(name = "expiration_year", nullable = false)
    private int expirationYear;

    /** Null means "no card-level cap"; the account's transfer limits still apply. */
    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(name = "monthly_limit", precision = 19, scale = 4)
    private BigDecimal monthlyLimit;

    /** Per-day cash withdrawal cap. Null means only {@link #dailyLimit} applies. */
    @Column(name = "atm_limit", precision = 19, scale = 4)
    private BigDecimal atmLimit;

    /** Per-day cap on card-not-present spending. Null means only {@link #dailyLimit} applies. */
    @Column(name = "online_limit", precision = 19, scale = 4)
    private BigDecimal onlineLimit;

    /*
     * Usage controls.
     *
     * Each switch names a channel the card can be used through. They exist so a cardholder can
     * narrow their own exposure without freezing the card outright -- turning off cash withdrawals
     * on a card that only pays for a subscription, for example, costs them nothing and removes a
     * whole class of misuse.
     *
     * Defaults mirror the migration. The magnetic stripe is the exception: it is the only channel
     * with no cryptographic authentication, so it starts off and must be asked for.
     */
    @Column(name = "online_payments_enabled", nullable = false)
    private boolean onlinePaymentsEnabled = true;

    @Column(name = "contactless_enabled", nullable = false)
    private boolean contactlessEnabled = true;

    @Column(name = "atm_withdrawals_enabled", nullable = false)
    private boolean atmWithdrawalsEnabled = true;

    @Column(name = "international_enabled", nullable = false)
    private boolean internationalEnabled = true;

    @Column(name = "magnetic_stripe_enabled", nullable = false)
    private boolean magneticStripeEnabled = false;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Card() {
        // for JPA
    }

    /**
     * Issues a card. The caller supplies the already-encrypted number, so the plaintext never
     * enters the entity and cannot be persisted by accident.
     */
    public static Card issue(
            BankAccount account,
            User owner,
            CardType cardType,
            CardDesign design,
            CardNetwork network,
            String label,
            EncryptedPan pan,
            Instant now) {

        Card card = new Card();
        card.account = account;
        card.owner = owner;
        card.cardType = cardType;
        card.design = design;
        card.network = network;
        card.label = label;
        card.bin = pan.bin();
        card.lastFour = pan.lastFour();
        card.panCiphertext = pan.ciphertext();
        card.panFingerprint = pan.fingerprint();
        card.cardholderName = owner.fullName().toUpperCase(java.util.Locale.ROOT);
        card.status = CardStatus.ACTIVE;

        YearMonth expiry = YearMonth.from(LocalDate.ofInstant(now, ZoneOffset.UTC)).plusYears(VALIDITY_YEARS);
        card.expirationMonth = expiry.getMonthValue();
        card.expirationYear = expiry.getYear();
        return card;
    }

    /** The number as it is safe to render: {@code 528412 •••••• 7351} collapsed to its two ends. */
    public String maskedNumber() {
        return bin.substring(0, 4) + " •••• •••• " + lastFour;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }

    /** True once the last day of the expiry month has passed. */
    public boolean isExpiredAt(Instant now) {
        YearMonth expiry = YearMonth.of(expirationYear, expirationMonth);
        return YearMonth.from(LocalDate.ofInstant(now, ZoneOffset.UTC)).isAfter(expiry);
    }

    /**
     * The status as it should be reported, taking the calendar into account.
     *
     * <p>Expiry is derived rather than written by a scheduled job: a job that fails to run would
     * leave expired cards looking active, which is exactly the wrong direction to fail in.
     */
    public CardStatus effectiveStatus(Instant now) {
        if (status == CardStatus.CANCELLED) {
            return CardStatus.CANCELLED;
        }
        return isExpiredAt(now) ? CardStatus.EXPIRED : status;
    }

    public void freeze(Instant now) {
        requireMutable(now);
        status = CardStatus.FROZEN;
        frozenAt = now;
    }

    public void unfreeze(Instant now) {
        requireMutable(now);
        status = CardStatus.ACTIVE;
        frozenAt = null;
    }

    public void cancel() {
        if (status == CardStatus.CANCELLED) {
            throw new BusinessRuleException(ErrorCode.CARD_NOT_ACTIVE, "Card is already cancelled");
        }
        status = CardStatus.CANCELLED;
    }

    /**
     * Guards an operation that only makes sense on a card that is still in play.
     *
     * @throws BusinessRuleException if the card has expired or been cancelled
     */
    public void requireMutable(Instant now) {
        CardStatus effective = effectiveStatus(now);
        if (effective == CardStatus.EXPIRED) {
            throw new BusinessRuleException(ErrorCode.CARD_EXPIRED);
        }
        if (!effective.isMutable()) {
            throw new BusinessRuleException(ErrorCode.CARD_NOT_ACTIVE);
        }
    }

    /** The encrypted form of a card number, produced by the crypto service. */
    public record EncryptedPan(String bin, String lastFour, String ciphertext, String fingerprint) {}
}
