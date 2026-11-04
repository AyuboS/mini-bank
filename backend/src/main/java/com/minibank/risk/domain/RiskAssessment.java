package com.minibank.risk.domain;

import java.util.List;

/**
 * The engine's verdict on a transfer.
 *
 * @param decision what to do with the transfer
 * @param riskScore accumulated score, clamped to 0-100
 * @param signals the rules that fired, in the order they were evaluated
 */
public record RiskAssessment(RiskDecision decision, int riskScore, List<RiskSignal> signals) {

    public RiskAssessment {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public static RiskAssessment allow() {
        return new RiskAssessment(RiskDecision.ALLOW, 0, List.of());
    }

    /** The human readable explanations, for API responses and audit metadata. */
    public List<String> reasons() {
        return signals.stream().map(RiskSignal::reason).toList();
    }

    public boolean isBlocked() {
        return decision == RiskDecision.BLOCK;
    }

    public boolean requiresReview() {
        return decision == RiskDecision.REVIEW;
    }
}
