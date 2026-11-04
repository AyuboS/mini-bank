package com.minibank.verification.service;

import com.minibank.verification.domain.VerificationCode;
import com.minibank.verification.repository.VerificationCodeRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts wrong guesses at a verification code.
 *
 * <p>A separate bean for exactly the reason {@link com.minibank.auth.service.LoginAttemptService} is
 * one: a wrong code has to increment a counter <em>and</em> end in an exception. Incrementing inside
 * the transaction that then rolls back would undo the increment, and the attempt limit — the only
 * thing standing between a six-digit code and unlimited guessing — would silently never fire. Being
 * a distinct bean is what makes Spring's proxy honour {@code REQUIRES_NEW}.
 */
@Service
public class VerificationAttemptService {

    private static final Logger log = LoggerFactory.getLogger(VerificationAttemptService.class);

    private final VerificationCodeRepository repository;

    public VerificationAttemptService(VerificationCodeRepository repository) {
        this.repository = repository;
    }

    /**
     * Records one wrong guess, in its own committed transaction.
     *
     * @return true when this failure used up the last permitted attempt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailure(UUID codeId) {
        VerificationCode record = repository.findById(codeId).orElse(null);
        if (record == null) {
            return false;
        }
        record.registerFailedAttempt();
        repository.save(record);

        boolean exhausted = !record.hasAttemptsLeft();
        if (exhausted) {
            log.warn("Verification code {} burned after {} failed attempts", codeId, record.getAttempts());
        }
        return exhausted;
    }
}
