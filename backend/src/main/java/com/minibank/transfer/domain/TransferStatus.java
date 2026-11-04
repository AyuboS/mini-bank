package com.minibank.transfer.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a transfer, with the legal transitions encoded on the enum itself.
 *
 * <pre>
 *   PENDING ──▶ PROCESSING ──▶ COMPLETED ──▶ REVERSED
 *      │             │
 *      └─────────────┴──────▶ FAILED
 * </pre>
 *
 * <p>PENDING is not decorative: it is the state a transfer sits in when the risk engine asked for
 * manual review, waiting for an administrator to approve or reject it. Money has not moved yet.
 *
 * <p>COMPLETED and FAILED are settled outcomes. The only move out of COMPLETED is REVERSED, and that
 * is performed by writing compensating ledger entries rather than by rewriting history.
 */
public enum TransferStatus {
    /** Created, awaiting a decision. No money has moved. */
    PENDING,
    /** Accepted and being settled inside the database transaction. */
    PROCESSING,
    /** Settled. Ledger entries exist and balances have moved. */
    COMPLETED,
    /** Terminal failure. No ledger entries exist for this transfer. */
    FAILED,
    /** Settled and subsequently reversed by a compensating transfer. */
    REVERSED;

    private static final Set<TransferStatus> FROM_PENDING = EnumSet.of(PROCESSING, FAILED);
    private static final Set<TransferStatus> FROM_PROCESSING = EnumSet.of(COMPLETED, FAILED);
    private static final Set<TransferStatus> FROM_COMPLETED = EnumSet.of(REVERSED);

    public boolean canTransitionTo(TransferStatus target) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case PROCESSING -> FROM_PROCESSING.contains(target);
            case COMPLETED -> FROM_COMPLETED.contains(target);
            case FAILED, REVERSED -> false;
        };
    }

    public boolean isSettled() {
        return this == COMPLETED || this == REVERSED;
    }

    public boolean isTerminal() {
        return this == FAILED || this == REVERSED;
    }
}
