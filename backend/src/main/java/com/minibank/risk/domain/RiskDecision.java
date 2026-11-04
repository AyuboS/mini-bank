package com.minibank.risk.domain;

/** What the risk engine concluded about a proposed transfer. */
public enum RiskDecision {
    /** Settle immediately. */
    ALLOW,
    /** Hold as PENDING for an administrator to approve or reject. No money moves yet. */
    REVIEW,
    /** Refuse outright. The transfer is recorded as FAILED so the attempt stays visible. */
    BLOCK
}
