package com.minibank.transfer.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.ledger.domain.EntryType;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.domain.TransferType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A transfer seen from one side of the transaction.
 *
 * <p>A statement line is inherently relative: the same transfer is money out for the payer and money
 * in for the payee. {@code direction} and {@code counterpartyAccountNumber} are therefore resolved
 * against the accounts the caller is looking at, so the client does not have to work out which side
 * it is on.
 */
@Schema(name = "TransactionResponse")
public record TransactionResponse(
        UUID transferId,
        @Schema(example = "TRX-2026-A7F3K92M") String reference,
        TransferType type,
        TransferStatus status,
        @Schema(description = "DEBIT when money left the viewed account, CREDIT when it arrived")
                EntryType direction,
        @Schema(example = "MB-119384756201") String counterpartyAccountNumber,
        @Schema(example = "100.5000") BigDecimal amount,
        CurrencyCode currency,
        String description,
        TransactionCategory category,
        Instant createdAt,
        Instant completedAt) {

    /** Renders a transfer from the point of view of the given set of account ids. */
    public static TransactionResponse from(Transfer transfer, Set<UUID> viewedAccountIds) {
        boolean outgoing = viewedAccountIds.contains(transfer.getSourceAccount().getId());
        return new TransactionResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getTransferType(),
                transfer.getStatus(),
                outgoing ? EntryType.DEBIT : EntryType.CREDIT,
                outgoing
                        ? transfer.getDestinationAccount().getAccountNumber()
                        : transfer.getSourceAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCategory(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt());
    }
}
