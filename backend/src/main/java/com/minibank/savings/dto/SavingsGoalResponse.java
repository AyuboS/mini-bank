package com.minibank.savings.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.savings.domain.SavingsGoal;
import com.minibank.savings.domain.SavingsGoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal and its progress.
 *
 * <p>{@code saved} is the backing account's balance, not a stored counter — see {@link SavingsGoal}
 * for why that distinction matters.
 */
@Schema(name = "SavingsGoalResponse")
public record SavingsGoalResponse(
        UUID id,
        @Schema(description = "The account holding the money") UUID accountId,
        @Schema(example = "MB-482910375512") String accountNumber,
        @Schema(example = "Thailand trip") String name,
        @Schema(example = "1500.0000") BigDecimal targetAmount,
        @Schema(example = "650.0000") BigDecimal saved,
        @Schema(example = "850.0000") BigDecimal remaining,
        @Schema(example = "43") int progressPercent,
        CurrencyCode currency,
        LocalDate deadline,
        SavingsGoalStatus status,
        String accent,
        Instant createdAt) {

    public static SavingsGoalResponse from(SavingsGoal goal) {
        return new SavingsGoalResponse(
                goal.getId(),
                goal.getAccount().getId(),
                goal.getAccount().getAccountNumber(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.saved(),
                goal.remaining(),
                goal.progressPercent(),
                goal.getCurrency(),
                goal.getDeadline(),
                goal.getStatus(),
                goal.getAccent(),
                goal.getCreatedAt());
    }
}
