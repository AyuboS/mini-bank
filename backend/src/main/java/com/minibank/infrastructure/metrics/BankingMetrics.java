package com.minibank.infrastructure.metrics;

import com.minibank.common.money.CurrencyCode;
import com.minibank.risk.domain.RiskDecision;
import com.minibank.transfer.domain.TransferStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Application metrics that describe the business, not just the JVM.
 *
 * <p>Heap usage and request latency come free from Actuator. What an operator actually needs during
 * an incident is whether transfers are still settling, whether the failure rate has moved, and
 * whether the risk engine has started blocking everything — so those are counted explicitly.
 *
 * <p>Counters are tagged rather than duplicated per outcome, so a dashboard can sum across statuses
 * or break down by one without new instrumentation.
 */
@Component
public class BankingMetrics {

    private final MeterRegistry registry;
    private final Counter idempotentReplays;
    private final Counter loginFailures;

    public BankingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.idempotentReplays = Counter.builder("minibank.idempotency.replays")
                .description("Requests answered from a stored idempotent response instead of being executed")
                .register(registry);
        this.loginFailures = Counter.builder("minibank.login.failures")
                .description("Failed authentication attempts")
                .register(registry);
    }

    /** Records the outcome of a transfer attempt, and the value moved when it settled. */
    public void recordTransfer(TransferStatus status, CurrencyCode currency, BigDecimal amount) {
        Counter.builder("minibank.transfers")
                .description("Transfer attempts by final status")
                .tag("status", status.name())
                .tag("currency", currency.name())
                .register(registry)
                .increment();

        if (status == TransferStatus.COMPLETED) {
            Counter.builder("minibank.transfers.amount")
                    .description("Total value settled")
                    .tag("currency", currency.name())
                    .register(registry)
                    .increment(amount.doubleValue());
        }
    }

    public void recordRiskDecision(RiskDecision decision) {
        Counter.builder("minibank.risk.decisions")
                .description("Risk engine verdicts")
                .tag("decision", decision.name())
                .register(registry)
                .increment();
    }

    public void recordIdempotentReplay() {
        idempotentReplays.increment();
    }

    public void recordLoginFailure() {
        loginFailures.increment();
    }
}
