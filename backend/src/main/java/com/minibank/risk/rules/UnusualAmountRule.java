package com.minibank.risk.rules;

import com.minibank.risk.config.RiskProperties;
import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskSignal;
import com.minibank.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rule 3: the transfer is far larger than this customer's own recent norm.
 *
 * <p>Relative to the individual rather than to a global figure, because "unusual" means something
 * different for a student and for a landlord. The rule stays silent when there is no history to
 * compare against, and below a configured floor, so ordinary small payments never trip it.
 */
@Component
public class UnusualAmountRule implements RiskRule {

    public static final String CODE = "UNUSUAL_AMOUNT";

    private final RiskProperties properties;
    private final TransferRepository transferRepository;

    public UnusualAmountRule(RiskProperties properties, TransferRepository transferRepository) {
        this.properties = properties;
        this.transferRepository = transferRepository;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskContext context) {
        var config = properties.unusualAmount();
        if (context.amount().compareTo(config.minimumAmount()) < 0) {
            return Optional.empty();
        }
        BigDecimal average = transferRepository.averageCompletedAmountSince(
                context.initiator().getId(), context.now().minus(config.historyWindow()));
        if (average == null || average.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal ceiling = average.multiply(config.multiplier());
        if (context.amount().compareTo(ceiling) <= 0) {
            return Optional.empty();
        }
        return Optional.of(RiskSignal.scored(
                CODE,
                config.score(),
                "Amount is more than %sx this customer's recent average of %s"
                        .formatted(
                                config.multiplier().stripTrailingZeros().toPlainString(),
                                average.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())));
    }
}
