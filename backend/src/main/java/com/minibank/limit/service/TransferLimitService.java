package com.minibank.limit.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.money.CurrencyCode;
import com.minibank.limit.config.TransferLimitProperties;
import com.minibank.limit.domain.TransferLimitOverride;
import com.minibank.limit.dto.EffectiveLimits;
import com.minibank.limit.dto.UpdateTransferLimitsRequest;
import com.minibank.limit.repository.TransferLimitOverrideRepository;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.user.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces per-transfer, daily and hourly transfer limits.
 *
 * <p>Limits are resolved once per check from configuration plus any per-customer override, so the
 * numbers live in one place instead of being scattered as literals through the transfer path.
 */
@Service
public class TransferLimitService {

    private static final Logger log = LoggerFactory.getLogger(TransferLimitService.class);

    /**
     * Statuses that count towards the daily total. Money awaiting review has already been committed
     * by the customer, so excluding it would let somebody exceed their ceiling by queueing transfers
     * faster than an administrator can approve them.
     */
    private static final List<TransferStatus> COUNTED_TOWARDS_DAILY =
            List.of(TransferStatus.PENDING, TransferStatus.PROCESSING, TransferStatus.COMPLETED);

    private final TransferLimitProperties defaults;
    private final TransferLimitOverrideRepository overrideRepository;
    private final TransferRepository transferRepository;
    private final AuditService auditService;
    private final Clock clock;

    public TransferLimitService(
            TransferLimitProperties defaults,
            TransferLimitOverrideRepository overrideRepository,
            TransferRepository transferRepository,
            AuditService auditService,
            Clock clock) {
        this.defaults = defaults;
        this.overrideRepository = overrideRepository;
        this.transferRepository = transferRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** The limits in force for a customer. */
    @Transactional(readOnly = true)
    public EffectiveLimits effectiveLimitsFor(UUID userId) {
        Optional<TransferLimitOverride> override = overrideRepository.findByUserId(userId);
        return new EffectiveLimits(
                override.map(TransferLimitOverride::getPerTransferMax).orElse(defaults.perTransferMax()),
                override.map(TransferLimitOverride::getDailyMax).orElse(defaults.dailyMax()),
                override.map(TransferLimitOverride::getMaxTransfersPerHour).orElse(defaults.maxTransfersPerHour()),
                override.isPresent());
    }

    /**
     * Rejects a transfer that would breach any limit.
     *
     * @throws BusinessRuleException with {@link ErrorCode#TRANSFER_LIMIT_EXCEEDED}
     */
    @Transactional(readOnly = true)
    public void checkWithinLimits(User initiator, BigDecimal amount, CurrencyCode currency) {
        EffectiveLimits limits = effectiveLimitsFor(initiator.getId());
        Instant now = clock.instant();

        if (amount.compareTo(limits.perTransferMax()) > 0) {
            throw limitExceeded(
                    "Transfer of %s %s exceeds the per-transfer limit of %s"
                            .formatted(plain(amount), currency, plain(limits.perTransferMax())));
        }

        Instant startOfDay = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal spentToday = transferRepository.sumAmountByInitiatorSince(
                initiator.getId(), startOfDay, COUNTED_TOWARDS_DAILY);
        if (spentToday.add(amount).compareTo(limits.dailyMax()) > 0) {
            throw limitExceeded(
                    "Transfer of %s %s would exceed the daily limit of %s (%s already sent today)"
                            .formatted(plain(amount), currency, plain(limits.dailyMax()), plain(spentToday)));
        }

        long lastHour = transferRepository.countByInitiatedByIdAndCreatedAtAfter(
                initiator.getId(), now.minus(Duration.ofHours(1)));
        if (lastHour >= limits.maxTransfersPerHour()) {
            throw limitExceeded(
                    "Limit of %d transfers per hour reached".formatted(limits.maxTransfersPerHour()));
        }
    }

    /** Creates or updates a customer's limit override. */
    @Transactional
    public EffectiveLimits updateOverride(
            User user, UpdateTransferLimitsRequest request, UUID adminId, String adminUsername) {
        TransferLimitOverride override = overrideRepository
                .findByUserId(user.getId())
                .orElseGet(() -> TransferLimitOverride.forUser(user));
        override.setPerTransferMax(request.perTransferMax());
        override.setDailyMax(request.dailyMax());
        override.setMaxTransfersPerHour(request.maxTransfersPerHour());
        override.setNote(request.note());
        overrideRepository.save(override);

        auditService.record(AuditEvent.success(AuditAction.TRANSFER_LIMITS_UPDATED)
                .by(adminId, adminUsername)
                .on("User", user.getId())
                .with("perTransferMax", request.perTransferMax())
                .with("dailyMax", request.dailyMax())
                .with("maxTransfersPerHour", request.maxTransfersPerHour()));
        log.info("Transfer limits for user {} updated by {}", user.getId(), adminUsername);
        return effectiveLimitsFor(user.getId());
    }

    private static BusinessRuleException limitExceeded(String message) {
        return new BusinessRuleException(ErrorCode.TRANSFER_LIMIT_EXCEEDED, message);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
