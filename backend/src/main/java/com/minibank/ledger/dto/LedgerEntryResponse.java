package com.minibank.ledger.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.ledger.domain.EntryType;
import com.minibank.ledger.domain.LedgerEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One posting of a double-entry pair. */
@Schema(name = "LedgerEntryResponse")
public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        @Schema(example = "MB-482910375512") String accountNumber,
        EntryType entryType,
        @Schema(example = "100.5000") BigDecimal amount,
        CurrencyCode currency,
        @Schema(description = "Account balance immediately after this entry was applied", example = "4150.0000")
                BigDecimal balanceAfter,
        Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccount().getId(),
                entry.getAccount().getAccountNumber(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getBalanceAfter(),
                entry.getCreatedAt());
    }
}
