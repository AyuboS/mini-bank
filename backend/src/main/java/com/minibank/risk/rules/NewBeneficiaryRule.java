package com.minibank.risk.rules;

import com.minibank.beneficiary.repository.BeneficiaryRepository;
import com.minibank.risk.config.RiskProperties;
import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskSignal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rule 5: a large payment to a payee that was added moments ago.
 *
 * <p>"Add the payee, send everything, disappear" is the standard shape of both account takeover and
 * authorised-push-payment fraud, which is why the combination scores higher than either half alone.
 */
@Component
public class NewBeneficiaryRule implements RiskRule {

    public static final String CODE = "NEW_BENEFICIARY_LARGE_TRANSFER";

    private final RiskProperties properties;
    private final BeneficiaryRepository beneficiaryRepository;

    public NewBeneficiaryRule(RiskProperties properties, BeneficiaryRepository beneficiaryRepository) {
        this.properties = properties;
        this.beneficiaryRepository = beneficiaryRepository;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskContext context) {
        var config = properties.newBeneficiary();
        if (context.amount().compareTo(config.amountThreshold()) < 0) {
            return Optional.empty();
        }
        boolean recentlyAdded = beneficiaryRepository.existsRecentlyAddedForOwner(
                context.initiator().getId(),
                context.destinationAccount().getAccountNumber(),
                context.now().minus(config.window()));
        if (!recentlyAdded) {
            return Optional.empty();
        }
        return Optional.of(RiskSignal.scored(
                CODE,
                config.score(),
                "Large transfer to a beneficiary added within the last %d minutes"
                        .formatted(config.window().toMinutes())));
    }
}
