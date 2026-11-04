package com.minibank.risk.rules;

import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskSignal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rule 6: the transfer would move almost the entire balance of the source account.
 *
 * <p>Emptying an account in one movement is the shape of an account takeover. It is scored rather
 * than blocked, because it is also exactly what a legitimate customer does when they close an
 * account or pay a deposit — the point is to combine it with other signals, not to refuse it alone.
 *
 * <p>Note that a frozen or closed destination is <em>not</em> handled here. That condition is not a
 * matter of degree, so it is enforced as a hard validation in the transfer service, which returns a
 * precise {@code ACCOUNT_FROZEN} rather than a generic risk refusal.
 */
@Component
public class BalanceSweepRule implements RiskRule {

    public static final String CODE = "BALANCE_SWEEP";

    /** Proportion of the balance above which the transfer counts as a sweep. */
    private static final BigDecimal SWEEP_RATIO = new BigDecimal("0.90");

    private static final int SCORE = 25;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskContext context) {
        BigDecimal balance = context.sourceAccount().getBalance();
        if (balance.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal threshold = balance.multiply(SWEEP_RATIO);
        if (context.amount().compareTo(threshold) < 0) {
            return Optional.empty();
        }
        BigDecimal percentage = context.amount()
                .multiply(BigDecimal.valueOf(100))
                .divide(balance, 0, RoundingMode.HALF_UP);
        return Optional.of(RiskSignal.scored(
                CODE, SCORE, "Transfer moves %s%% of the available balance".formatted(percentage.toPlainString())));
    }
}
