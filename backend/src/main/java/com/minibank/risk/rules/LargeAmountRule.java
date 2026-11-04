package com.minibank.risk.rules;

import com.minibank.risk.config.RiskProperties;
import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskSignal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Rule 1: the transfer amount exceeds an absolute threshold. */
@Component
public class LargeAmountRule implements RiskRule {

    public static final String CODE = "LARGE_AMOUNT";

    private final RiskProperties properties;

    public LargeAmountRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskContext context) {
        var config = properties.largeAmount();
        if (context.amount().compareTo(config.threshold()) < 0) {
            return Optional.empty();
        }
        return Optional.of(RiskSignal.scored(
                CODE,
                config.score(),
                "Transfer amount %s %s is at or above the review threshold of %s"
                        .formatted(context.amount().stripTrailingZeros().toPlainString(),
                                context.currency(),
                                config.threshold().stripTrailingZeros().toPlainString())));
    }
}
