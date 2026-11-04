package com.minibank.risk.service;

import com.minibank.risk.config.RiskProperties;
import com.minibank.risk.domain.RiskAssessment;
import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskDecision;
import com.minibank.risk.domain.RiskSignal;
import com.minibank.risk.rules.RiskRule;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A rules-based risk scorer.
 *
 * <p>This is deliberately not presented as bank-grade fraud detection. Real systems combine device
 * fingerprinting, behavioural models and shared industry intelligence. What this demonstrates is the
 * shape such a system takes: independent rules, an accumulated score, configurable thresholds, and a
 * decision that is recorded against the transfer so that any hold can be explained after the fact.
 *
 * <p>Rules are collected by dependency injection, so adding one is a new {@link RiskRule} bean.
 * A rule that throws is contained rather than allowed to fail the transfer: a bug in a heuristic
 * must not take down payments, so the failure is logged and the remaining rules still run.
 */
@Service
public class RiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);
    private static final int MAX_SCORE = 100;

    private final List<RiskRule> rules;
    private final RiskProperties properties;

    public RiskEngine(List<RiskRule> rules, RiskProperties properties) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
        log.info("Risk engine initialised with {} rules: {}", rules.size(), rules.stream().map(RiskRule::code).toList());
    }

    /** Evaluates every rule and turns the accumulated score into a decision. */
    @Transactional(readOnly = true)
    public RiskAssessment assess(RiskContext context) {
        List<RiskSignal> signals = new ArrayList<>();
        boolean blocking = false;
        int score = 0;

        for (RiskRule rule : rules) {
            try {
                var signal = rule.evaluate(context);
                if (signal.isPresent()) {
                    RiskSignal fired = signal.get();
                    signals.add(fired);
                    score += fired.score();
                    blocking |= fired.blocking();
                }
            } catch (RuntimeException ex) {
                log.error("Risk rule {} failed; continuing without it", rule.code(), ex);
            }
        }

        int cappedScore = Math.min(score, MAX_SCORE);
        RiskDecision decision = decide(cappedScore, blocking);
        if (decision != RiskDecision.ALLOW) {
            log.info(
                    "Risk decision {} (score {}) for transfer of {} {} by user {}: {}",
                    decision,
                    cappedScore,
                    context.amount(),
                    context.currency(),
                    context.initiator().getId(),
                    signals.stream().map(RiskSignal::code).toList());
        }
        return new RiskAssessment(decision, cappedScore, signals);
    }

    private RiskDecision decide(int score, boolean blocking) {
        if (blocking || score >= properties.thresholds().block()) {
            return RiskDecision.BLOCK;
        }
        if (score >= properties.thresholds().review()) {
            return RiskDecision.REVIEW;
        }
        return RiskDecision.ALLOW;
    }
}
