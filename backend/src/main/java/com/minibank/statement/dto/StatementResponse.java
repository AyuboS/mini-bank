package com.minibank.statement.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.transfer.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A period's account statement.
 *
 * <p>The opening balance is reconstructed from the ledger rather than stored: it is the closing
 * balance carried back through every entry in the period. Doing it that way means the statement can
 * never disagree with the ledger, and that a statement for a past month is still correct after a
 * reversal is posted against it.
 */
@Schema(name = "StatementResponse")
public record StatementResponse(
        UUID accountId,
        @Schema(example = "MB-482910375512") String accountNumber,
        CurrencyCode currency,
        @Schema(example = "2026-08") String period,
        Instant from,
        Instant to,
        @Schema(example = "1820.0000") BigDecimal openingBalance,
        @Schema(example = "3200.0000") BigDecimal totalIn,
        @Schema(example = "1240.0000") BigDecimal totalOut,
        @Schema(example = "3780.0000") BigDecimal closingBalance,
        int transactionCount,
        List<TransactionResponse> transactions) {}
