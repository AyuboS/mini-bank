package com.minibank.verification.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes verification records that are past every deadline they carry.
 *
 * <p>Not housekeeping for its own sake. This is the highest-churn table in the application and
 * every row holds a phone number or an email address; keeping spent ones indefinitely would turn a
 * transient artefact of signing in into a standing collection of customer contact details, which is
 * exactly the kind of data a breach is measured by.
 *
 * <p>Hourly is ample: nothing depends on the row being gone promptly, only on it not accumulating.
 */
@Component
public class VerificationCleanupJob {

    private final VerificationService verificationService;

    public VerificationCleanupJob(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void purge() {
        verificationService.purgeExpired();
    }
}
