package com.minibank.risk.rules;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.domain.AuditOutcome;
import com.minibank.audit.repository.AuditLogRepository;
import com.minibank.risk.config.RiskProperties;
import com.minibank.risk.domain.RiskContext;
import com.minibank.risk.domain.RiskSignal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rule 4: failed authentications shortly before the transfer.
 *
 * <p>A successful login preceded by several failures is consistent with somebody who has just
 * guessed or phished their way in, so the money movement that follows deserves extra scrutiny.
 */
@Component
public class RecentFailedLoginsRule implements RiskRule {

    public static final String CODE = "RECENT_FAILED_LOGINS";

    private final RiskProperties properties;
    private final AuditLogRepository auditLogRepository;

    public RecentFailedLoginsRule(RiskProperties properties, AuditLogRepository auditLogRepository) {
        this.properties = properties;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskSignal> evaluate(RiskContext context) {
        var config = properties.failedLogins();
        long failures = auditLogRepository.countByUserIdAndActionAndOutcomeAndCreatedAtAfter(
                context.initiator().getId(),
                AuditAction.LOGIN_FAILED,
                AuditOutcome.FAILURE,
                context.now().minus(config.window()));
        if (failures < config.threshold()) {
            return Optional.empty();
        }
        return Optional.of(RiskSignal.scored(
                CODE,
                config.score(),
                "%d failed login attempts in the last %d minutes"
                        .formatted(failures, config.window().toMinutes())));
    }
}
