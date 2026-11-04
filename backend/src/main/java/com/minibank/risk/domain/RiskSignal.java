package com.minibank.risk.domain;

/**
 * One rule's contribution to a risk assessment.
 *
 * @param code stable identifier of the rule that fired, for metrics and dashboards
 * @param score points added to the transfer's risk total
 * @param reason human readable explanation, surfaced to administrators
 * @param blocking when true the transfer is refused regardless of the accumulated score
 */
public record RiskSignal(String code, int score, String reason, boolean blocking) {

    public static RiskSignal scored(String code, int score, String reason) {
        return new RiskSignal(code, score, reason, false);
    }

    public static RiskSignal blocking(String code, String reason) {
        return new RiskSignal(code, 100, reason, true);
    }
}
