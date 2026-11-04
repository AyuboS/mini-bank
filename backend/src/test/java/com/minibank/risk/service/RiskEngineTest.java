package com.minibank.risk.service;

import static com.minibank.risk.RiskTestFixtures.context;
import static com.minibank.risk.RiskTestFixtures.defaultProperties;
import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskDecision;
import com.minibank.risk.domain.RiskSignal;
import com.minibank.risk.rules.RiskRule;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RiskEngineTest {

    /** A rule that always returns the same signal, so the engine's aggregation is what is measured. */
    private static RiskRule fixedRule(String code, int score) {
        return new RiskRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public Optional<RiskSignal> evaluate(RiskContext ctx) {
                return Optional.of(RiskSignal.scored(code, score, code + " fired"));
            }
        };
    }

    private static RiskRule silentRule(String code) {
        return new RiskRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public Optional<RiskSignal> evaluate(RiskContext ctx) {
                return Optional.empty();
            }
        };
    }

    private static RiskRule blockingRule(String code) {
        return new RiskRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public Optional<RiskSignal> evaluate(RiskContext ctx) {
                return Optional.of(RiskSignal.blocking(code, code + " blocked"));
            }
        };
    }

    private static RiskRule explodingRule(String code) {
        return new RiskRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public Optional<RiskSignal> evaluate(RiskContext ctx) {
                throw new IllegalStateException("rule is broken");
            }
        };
    }

    @Test
    void allowsWhenNoRuleFires() {
        var engine = new RiskEngine(List.of(silentRule("A"), silentRule("B")), defaultProperties());

        var assessment = engine.assess(context("10"));

        assertThat(assessment.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(assessment.riskScore()).isZero();
        assertThat(assessment.signals()).isEmpty();
    }

    @Test
    void allowsWhenTheScoreStaysBelowTheReviewThreshold() {
        var engine = new RiskEngine(List.of(fixedRule("A", 30), fixedRule("B", 19)), defaultProperties());

        assertThat(engine.assess(context("10")).decision()).isEqualTo(RiskDecision.ALLOW);
    }

    @Test
    void holdsForReviewAtTheReviewThreshold() {
        var engine = new RiskEngine(List.of(fixedRule("A", 30), fixedRule("B", 20)), defaultProperties());

        var assessment = engine.assess(context("10"));

        assertThat(assessment.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(assessment.riskScore()).isEqualTo(50);
        assertThat(assessment.requiresReview()).isTrue();
    }

    @Test
    void blocksAtTheBlockThreshold() {
        var engine = new RiskEngine(
                List.of(fixedRule("A", 35), fixedRule("B", 30), fixedRule("C", 20)), defaultProperties());

        var assessment = engine.assess(context("10"));

        assertThat(assessment.decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(assessment.isBlocked()).isTrue();
    }

    @Test
    void aBlockingRuleOverridesALowScore() {
        var engine = new RiskEngine(List.of(blockingRule("HARD_STOP")), defaultProperties());

        assertThat(engine.assess(context("10")).decision()).isEqualTo(RiskDecision.BLOCK);
    }

    @Test
    void capsTheScoreAtOneHundred() {
        var engine = new RiskEngine(
                List.of(fixedRule("A", 80), fixedRule("B", 80), fixedRule("C", 80)), defaultProperties());

        assertThat(engine.assess(context("10")).riskScore()).isEqualTo(100);
    }

    @Test
    void collectsEveryReasonForTheOperatorToRead() {
        var engine = new RiskEngine(List.of(fixedRule("A", 30), silentRule("B"), fixedRule("C", 25)), defaultProperties());

        assertThat(engine.assess(context("10")).reasons()).containsExactly("A fired", "C fired");
    }

    @Test
    void aBrokenRuleDoesNotTakeDownPayments() {
        // A bug in one heuristic must not fail every transfer; the rest still run.
        var engine = new RiskEngine(
                List.of(fixedRule("A", 30), explodingRule("BROKEN"), fixedRule("C", 20)), defaultProperties());

        var assessment = engine.assess(context("10"));

        assertThat(assessment.riskScore()).isEqualTo(50);
        assertThat(assessment.signals()).hasSize(2);
    }
}
