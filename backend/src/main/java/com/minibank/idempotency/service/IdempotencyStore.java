package com.minibank.idempotency.service;

import com.minibank.common.error.ApiException;
import com.minibank.common.error.ErrorCode;
import com.minibank.idempotency.domain.IdempotencyRecord;
import com.minibank.idempotency.domain.IdempotencyStatus;
import com.minibank.idempotency.repository.IdempotencyRecordRepository;
import com.minibank.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional persistence for idempotency records.
 *
 * <p>Every method commits in its own transaction. That is the whole point: the reservation has to
 * survive independently of the business transaction it guards, and the outcome has to be recorded
 * even when the business transaction rolls back — otherwise a failed transfer would leave a
 * dangling IN_PROGRESS reservation that blocks the client's own retry.
 *
 * <p>Separated from {@link IdempotencyService} because Spring's transaction proxy does not
 * intercept self-invocation: an orchestration method calling {@code this.reserve()} would silently
 * run in the caller's transaction instead of a new one.
 */
@Service
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final IdempotencyRecordRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public IdempotencyStore(
            IdempotencyRecordRepository repository, UserRepository userRepository, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Inserts a fresh reservation.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException if the key is already taken;
     *     the caller resolves the existing record instead
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID reserve(String key, UUID userId, String endpoint, String requestHash) {
        var user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        IdempotencyRecord record = IdempotencyRecord.reserve(key, user, endpoint, requestHash);
        // Flushing here surfaces the unique-constraint violation now, rather than at an arbitrary
        // later point in the transaction where it would be much harder to attribute.
        return repository.saveAndFlush(record).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyRecord> find(UUID userId, String endpoint, String key) {
        return repository.findByUserIdAndEndpointAndIdempotencyKey(userId, endpoint, key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID recordId, int responseStatus, String responseBody, UUID resourceId) {
        repository.findById(recordId).ifPresent(record -> record.complete(responseStatus, responseBody, resourceId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID recordId, int responseStatus, String responseBody) {
        repository.findById(recordId).ifPresent(record -> record.fail(responseStatus, responseBody));
    }

    /**
     * Removes a reservation entirely.
     *
     * <p>Used when the operation failed for a reason that is not the client's answer — an
     * infrastructure error rather than a business rule. Keeping the key would permanently poison it
     * and deny the client the retry that idempotency exists to make safe.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID recordId) {
        repository.deleteById(recordId);
    }

    /** Drops records old enough that no client would still be retrying them. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeExpiredRecords() {
        int deleted = repository.deleteAllCreatedBefore(clock.instant().minus(RETENTION));
        if (deleted > 0) {
            log.info("Purged {} idempotency records older than {} days", deleted, RETENTION.toDays());
        }
    }

    /** Convenience for the orchestrator: is this record still executing? */
    public static boolean isInProgress(IdempotencyRecord record) {
        return record.getStatus() == IdempotencyStatus.IN_PROGRESS;
    }
}
