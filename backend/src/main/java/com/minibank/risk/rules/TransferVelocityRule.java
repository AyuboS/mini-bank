package com.minibank.risk.rules;

import com.minibank.risk.config.RiskProperties;
import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskSignal;
import com.minibank.transfer.repository.TransferRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rule 2: several transfers started within a short window.
 *
 * <p>Bursts are the signature of an attacker draining an account before the owner notices, and of a
 * script retrying without idempotency.
 */
@Component
public class TransferVelocityRule implements RiskRule {

    public static final String CODE = "HIGH_VELOCITY";

    private final RiskProperties properties;
    private final TransferRepository transferRepository;

    public TransferVelocityRule(RiskProperties properties, TransferRepository transferRepository) {
        this.properties = properties;
        this.transferRepository = transferRepository;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskContext context) {
        var config = properties.velocity();
        long recent = transferRepository.countByInitiatedByIdAndCreatedAtAfter(
                context.initiator().getId(), context.now().minus(config.window()));
        if (recent < config.maxTransfers()) {
            return Optional.empty();
        }
        return Optional.of(RiskSignal.scored(
                CODE,
                config.score(),
                "%d transfers started in the last %d minutes".formatted(recent, config.window().toMinutes())));
    }
}
