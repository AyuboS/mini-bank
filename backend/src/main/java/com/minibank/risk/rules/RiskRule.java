package com.minibank.risk.rules;

import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskSignal;
import java.util.Optional;

/**
 * A single risk heuristic.
 *
 * <p>Rules are Spring beans collected by the engine, so adding one is a matter of writing a class
 * and annotating it — no registry to update and no switch statement to extend.
 */
public interface RiskRule {

    /** Stable identifier, used in metrics and in the reasons returned to administrators. */
    String code();

    /**
     * @return a signal when the rule fires, or empty when it has nothing to say
     */
    Optional<RiskSignal> evaluate(RiskContext context);
}
