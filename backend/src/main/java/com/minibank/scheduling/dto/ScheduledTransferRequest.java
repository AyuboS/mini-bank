package com.minibank.scheduling.dto;

import com.minibank.scheduling.domain.ScheduleFrequency;
import com.minibank.transfer.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Set up a standing order, or change one that has not finished. */
@Schema(name = "ScheduledTransferRequest")
public record ScheduledTransferRequest(
        @NotNull @Schema(description = "One of your own accounts") UUID fromAccountId,
        @NotNull
                @Pattern(regexp = "^MB-[0-9]{12}$", message = "must be a valid MBank account number")
                @Schema(example = "MB-482910375512")
                String toAccountNumber,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) @Schema(example = "500.00") BigDecimal amount,
        @Size(max = 140) @Schema(example = "Rent") String description,
        @Schema(example = "BILLS") TransactionCategory category,
        @NotNull @Schema(example = "MONTHLY") ScheduleFrequency frequency,
        @NotNull @Schema(description = "Today or later", example = "2026-09-01") LocalDate startOn,
        @Schema(description = "Optional. Null means the order runs until you cancel it.")
                LocalDate endsOn) {}
