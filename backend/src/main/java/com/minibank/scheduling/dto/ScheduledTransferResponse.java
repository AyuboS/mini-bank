package com.minibank.scheduling.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.scheduling.domain.ScheduleFrequency;
import com.minibank.scheduling.domain.ScheduleStatus;
import com.minibank.scheduling.domain.ScheduledTransfer;
import com.minibank.transfer.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A standing order and how it has been getting on. */
@Schema(name = "ScheduledTransferResponse")
public record ScheduledTransferResponse(
        UUID id,
        UUID fromAccountId,
        @Schema(example = "MB-482910375512") String fromAccountNumber,
        @Schema(example = "MB-119384756201") String toAccountNumber,
        String payeeName,
        BigDecimal amount,
        CurrencyCode currency,
        String description,
        TransactionCategory category,
        ScheduleFrequency frequency,
        LocalDate nextRunOn,
        LocalDate endsOn,
        ScheduleStatus status,
        Instant lastRunAt,
        @Schema(description = "Why the last attempt did not go through. Cleared by the next success.")
                String lastFailureReason,
        int runsCompleted,
        Instant createdAt) {

    public static ScheduledTransferResponse from(ScheduledTransfer order) {
        return new ScheduledTransferResponse(
                order.getId(),
                order.getSourceAccount().getId(),
                order.getSourceAccount().getAccountNumber(),
                order.getDestinationAccountNumber(),
                order.getPayeeName(),
                order.getAmount(),
                order.getCurrency(),
                order.getDescription(),
                order.getCategory(),
                order.getFrequency(),
                order.getNextRunOn(),
                order.getEndsOn(),
                order.getStatus(),
                order.getLastRunAt(),
                order.getLastFailureReason(),
                order.getRunsCompleted(),
                order.getCreatedAt());
    }
}
