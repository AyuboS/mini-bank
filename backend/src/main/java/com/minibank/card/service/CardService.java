package com.minibank.card.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.repository.BankAccountRepository;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.card.config.CardProperties;
import com.minibank.card.domain.Card;
import com.minibank.card.domain.CardDesign;
import com.minibank.card.domain.CardNetwork;
import com.minibank.card.domain.CardStatus;
import com.minibank.card.domain.CardType;
import com.minibank.card.dto.CardControlsRequest;
import com.minibank.card.dto.CardDetailsResponse;
import com.minibank.card.dto.CardSpendingResponse;
import com.minibank.card.dto.CreateCardRequest;
import com.minibank.card.dto.UpdateCardLimitsRequest;
import com.minibank.card.dto.UpdateCardRequest;
import com.minibank.card.repository.CardRepository;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.money.Money;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issuing and managing cards.
 *
 * <p>Ownership is enforced in exactly one place, {@link #requireOwnCard}, for the same reason
 * account access is: the moment that check is duplicated across endpoints, one of them will be
 * written without it. A card belonging to somebody else answers {@code CARD_NOT_FOUND} rather than
 * {@code FORBIDDEN}, so the endpoint cannot be used to discover which card ids exist.
 *
 * <p>Nothing here logs a card number, a fingerprint or a verification value. Log lines identify a
 * card by its UUID and, at most, its last four digits.
 */
@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    /**
     * Statuses whose money counts as spent. Matches the transfer limit rules: a payment awaiting
     * review has already been committed by the customer and must not be free of a cap.
     */
    private static final List<TransferStatus> SPENT =
            List.of(TransferStatus.PENDING, TransferStatus.PROCESSING, TransferStatus.COMPLETED);

    /** Enough attempts at a unique number that exhausting them means something is wrong. */
    private static final int UNIQUE_NUMBER_ATTEMPTS = 5;

    private final CardRepository cardRepository;
    private final BankAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransferRepository transferRepository;
    private final CardNumberGenerator numberGenerator;
    private final CardCryptoService crypto;
    private final AuditService auditService;
    private final CardProperties properties;
    private final Clock clock;

    public CardService(
            CardRepository cardRepository,
            BankAccountRepository accountRepository,
            UserRepository userRepository,
            TransferRepository transferRepository,
            CardNumberGenerator numberGenerator,
            CardCryptoService crypto,
            AuditService auditService,
            CardProperties properties,
            Clock clock) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transferRepository = transferRepository;
        this.numberGenerator = numberGenerator;
        this.crypto = crypto;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /** The caller's cards, cancelled ones omitted. */
    @Transactional(readOnly = true)
    public List<Card> listOwnedCards(UUID ownerId) {
        return cardRepository.findAllOwnedBy(ownerId, CardStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public Card getOwnCard(UUID cardId, AuthenticatedUser caller) {
        return requireOwnCard(cardId, caller);
    }

    /** Issues a card against one of the caller's accounts. */
    @Transactional
    public Card issue(CreateCardRequest request, AuthenticatedUser caller) {
        User owner = userRepository
                .findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        if (cardRepository.countByOwnerIdAndStatusNot(owner.getId(), CardStatus.CANCELLED)
                >= properties.maxPerUser()) {
            throw new BusinessRuleException(
                    ErrorCode.CARD_LIMIT_REACHED,
                    "A customer may hold at most %d cards".formatted(properties.maxPerUser()));
        }

        BankAccount account = resolveAccount(request.accountId(), owner.getId());
        CardNetwork network = request.network() == null ? CardNetwork.VISA : request.network();
        CardType type = request.type();
        CardDesign design = request.design();

        Card card = Card.issue(
                account,
                owner,
                type,
                design,
                network,
                labelFor(request.label(), type, design),
                uniquePan(network),
                clock.instant());
        Card saved = cardRepository.save(card);

        auditService.record(AuditEvent.success(AuditAction.CARD_ISSUED)
                .by(owner)
                .on("Card", saved.getId())
                .with("type", type)
                .with("design", design)
                .with("network", network)
                .with("lastFour", saved.getLastFour())
                .with("accountNumber", account.getAccountNumber()));
        log.info("Issued {} {} card ending {} for user {}", type, network, saved.getLastFour(), owner.getId());
        return saved;
    }

    /** Renames a card, restyles it, or both. */
    @Transactional
    public Card update(UUID cardId, UpdateCardRequest request, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        card.requireMutable(clock.instant());

        if (request.label() != null) {
            card.setLabel(request.label().trim());
        }
        if (request.design() != null) {
            card.setDesign(request.design());
        }

        auditService.record(AuditEvent.success(AuditAction.CARD_UPDATED)
                .by(caller.getId(), caller.getUsername())
                .on("Card", cardId)
                .with("label", request.label())
                .with("design", request.design()));
        return card;
    }

    @Transactional
    public Card freeze(UUID cardId, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        card.freeze(clock.instant());
        auditService.record(AuditEvent.success(AuditAction.CARD_FROZEN)
                .by(caller.getId(), caller.getUsername())
                .on("Card", cardId)
                .with("lastFour", card.getLastFour()));
        log.info("Card {} frozen by its owner", cardId);
        return card;
    }

    @Transactional
    public Card unfreeze(UUID cardId, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        card.unfreeze(clock.instant());
        auditService.record(AuditEvent.success(AuditAction.CARD_UNFROZEN)
                .by(caller.getId(), caller.getUsername())
                .on("Card", cardId)
                .with("lastFour", card.getLastFour()));
        log.info("Card {} unfrozen by its owner", cardId);
        return card;
    }

    /** Cancels a card permanently. */
    @Transactional
    public void cancel(UUID cardId, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        card.cancel();
        auditService.record(AuditEvent.success(AuditAction.CARD_CANCELLED)
                .by(caller.getId(), caller.getUsername())
                .on("Card", cardId)
                .with("lastFour", card.getLastFour()));
        log.info("Card {} cancelled by its owner", cardId);
    }

    /** Sets or clears the card's spending caps. */
    @Transactional
    public Card updateLimits(UUID cardId, UpdateCardLimitsRequest request, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        card.requireMutable(clock.instant());

        BigDecimal daily = request.clearDailyLimit() ? null : firstNonNull(request.dailyLimit(), card.getDailyLimit());
        BigDecimal monthly =
                request.clearMonthlyLimit() ? null : firstNonNull(request.monthlyLimit(), card.getMonthlyLimit());

        if (daily != null && monthly != null && monthly.compareTo(daily) < 0) {
            throw new BusinessRuleException(
                    ErrorCode.CARD_INVALID_LIMITS,
                    "A monthly limit below the daily limit would make the daily limit unreachable");
        }

        BigDecimal atm = request.clearAtmLimit() ? null : firstNonNull(request.atmLimit(), card.getAtmLimit());
        BigDecimal online =
                request.clearOnlineLimit() ? null : firstNonNull(request.onlineLimit(), card.getOnlineLimit());

        // A channel cap above the daily cap can never bind, so it is almost certainly a mistake on
        // the way in. Refusing it here is kinder than accepting a setting that quietly does nothing.
        requireNotAbove(atm, daily, "ATM");
        requireNotAbove(online, daily, "online payment");

        card.setDailyLimit(daily == null ? null : Money.normalize(daily));
        card.setMonthlyLimit(monthly == null ? null : Money.normalize(monthly));
        card.setAtmLimit(atm == null ? null : Money.normalize(atm));
        card.setOnlineLimit(online == null ? null : Money.normalize(online));

        auditService.record(AuditEvent.success(AuditAction.CARD_LIMITS_UPDATED)
                .by(caller.getId(), caller.getUsername())
                .on("Card", cardId)
                .with("dailyLimit", daily)
                .with("monthlyLimit", monthly)
                .with("atmLimit", atm)
                .with("onlineLimit", online));
        return card;
    }

    private static void requireNotAbove(BigDecimal channelLimit, BigDecimal dailyLimit, String channel) {
        if (channelLimit != null && dailyLimit != null && channelLimit.compareTo(dailyLimit) > 0) {
            throw new BusinessRuleException(
                    ErrorCode.CARD_INVALID_LIMITS,
                    "The %s limit cannot exceed the daily limit, which already caps it".formatted(channel));
        }
    }

    /**
     * Updates the card's usage controls.
     *
     * <p>Null fields are left untouched, so a client may send one switch without having to restate
     * the other four — the alternative is a request that turns off channels nobody asked about.
     *
     * <p>Runs the same ownership and mutability checks as every other card operation: controls are
     * a security feature, and a security feature enforced only in the client is decoration.
     */
    @Transactional
    public Card updateControls(UUID cardId, CardControlsRequest request, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        card.requireMutable(clock.instant());

        // AuditEvent is immutable and `with` returns a copy, so the accumulator is reassigned
        // rather than mutated. Only the switches the client actually sent are recorded, which keeps
        // the audit line a statement about what changed instead of a dump of the whole card.
        AuditEvent event = AuditEvent.success(AuditAction.CARD_CONTROLS_UPDATED)
                .by(caller.getId(), caller.getUsername())
                .on("Card", cardId);

        if (request.onlinePayments() != null) {
            card.setOnlinePaymentsEnabled(request.onlinePayments());
            event = event.with("onlinePayments", request.onlinePayments());
        }
        if (request.contactless() != null) {
            card.setContactlessEnabled(request.contactless());
            event = event.with("contactless", request.contactless());
        }
        if (request.atmWithdrawals() != null) {
            card.setAtmWithdrawalsEnabled(request.atmWithdrawals());
            event = event.with("atmWithdrawals", request.atmWithdrawals());
        }
        if (request.international() != null) {
            card.setInternationalEnabled(request.international());
            event = event.with("international", request.international());
        }
        if (request.magneticStripe() != null) {
            card.setMagneticStripeEnabled(request.magneticStripe());
            event = event.with("magneticStripe", request.magneticStripe());
        }

        auditService.record(event);
        log.info("Card controls updated for card {}", cardId);
        return card;
    }

    /**
     * Decrypts the number and derives the verification value.
     *
     * <p>The only path in the application that produces a full card number. It is audited on every
     * call — including the ones that succeed, which is the point: a reveal is a security event, and
     * the record of who revealed what and when is what makes a leak investigable.
     */
    @Transactional(readOnly = true)
    public CardDetailsResponse revealDetails(UUID cardId, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        Instant now = clock.instant();
        card.requireMutable(now);

        String pan = crypto.revealPan(card);
        auditService.recordIndependently(AuditEvent.success(AuditAction.CARD_DETAILS_REVEALED)
                .by(caller.getId(), caller.getUsername())
                .on("Card", cardId)
                .with("lastFour", card.getLastFour()));
        log.info("Card details revealed for card {} by its owner", cardId);

        return new CardDetailsResponse(
                card.getId(),
                card.getCardholderName(),
                pan,
                card.getExpirationMonth(),
                card.getExpirationYear(),
                crypto.verificationValue(card, pan),
                true,
                now);
    }

    /** How much of the card's caps the attached account has used today and this month. */
    @Transactional(readOnly = true)
    public CardSpendingResponse spending(UUID cardId, AuthenticatedUser caller) {
        Card card = requireOwnCard(cardId, caller);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        UUID accountId = card.getAccount().getId();

        BigDecimal spentToday = transferRepository.sumOutgoingByAccountSince(
                accountId, today.atStartOfDay(ZoneOffset.UTC).toInstant(), SPENT);
        BigDecimal spentThisMonth = transferRepository.sumOutgoingByAccountSince(
                accountId, today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant(), SPENT);

        return new CardSpendingResponse(
                cardId,
                card.getAccount().getCurrency(),
                spentToday,
                spentThisMonth,
                card.getDailyLimit(),
                card.getMonthlyLimit());
    }

    /**
     * Loads a card the caller owns.
     *
     * <p>An administrator is deliberately <em>not</em> granted access here. Reading another
     * customer's card is not part of any support workflow this application implements, and the
     * blanket admin bypass that account lookups carry would quietly extend to card numbers.
     */
    private Card requireOwnCard(UUID cardId, AuthenticatedUser caller) {
        Card card = cardRepository
                .findByIdWithRelations(cardId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CARD_NOT_FOUND));
        if (!card.isOwnedBy(caller.getId())) {
            log.warn("User {} attempted to access card {} they do not own", caller.getId(), cardId);
            throw new NotFoundException(ErrorCode.CARD_NOT_FOUND);
        }
        return card;
    }

    private BankAccount resolveAccount(UUID requestedId, UUID ownerId) {
        List<BankAccount> owned = accountRepository.findAllByOwnerId(ownerId);
        if (owned.isEmpty()) {
            throw new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (requestedId == null) {
            return owned.getFirst();
        }
        return owned.stream()
                .filter(account -> account.getId().equals(requestedId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /**
     * Generates a number that no card already holds.
     *
     * <p>A collision is vanishingly unlikely across a 16-digit space, but "unlikely" and "handled"
     * are different things, and the unique constraint on the fingerprint would otherwise surface as
     * a 500 at the worst possible moment.
     */
    private Card.EncryptedPan uniquePan(CardNetwork network) {
        for (int attempt = 0; attempt < UNIQUE_NUMBER_ATTEMPTS; attempt++) {
            Card.EncryptedPan pan = crypto.protect(numberGenerator.generate(network));
            if (!cardRepository.existsByPanFingerprint(pan.fingerprint())) {
                return pan;
            }
        }
        throw new IllegalStateException("Could not generate a unique card number after several attempts");
    }

    /** A sensible default name, so a card is never called "Card 1". */
    private static String labelFor(String requested, CardType type, CardDesign design) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String designName = design.name().charAt(0) + design.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        return type == CardType.VIRTUAL ? designName + " virtual" : designName;
    }

    private static BigDecimal firstNonNull(BigDecimal preferred, BigDecimal fallback) {
        return preferred != null ? preferred : fallback;
    }
}
