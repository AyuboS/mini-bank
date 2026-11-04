package com.minibank.idempotency.domain;

/** Progress of an idempotent operation. */
public enum IdempotencyStatus {
    /** Reserved and currently executing. A concurrent replay is rejected while in this state. */
    IN_PROGRESS,
    /** Finished successfully; the stored response is replayed verbatim. */
    COMPLETED,
    /** Finished with a business failure; the stored error is replayed verbatim. */
    FAILED
}
