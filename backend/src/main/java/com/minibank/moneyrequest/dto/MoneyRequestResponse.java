package com.minibank.moneyrequest.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.moneyrequest.domain.MoneyRequest;
import com.minibank.moneyrequest.domain.MoneyRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment request as either side sees it.
 *
 * <p>Both parties get the same shape, with {@code direction} saying which side the caller is on.
 * The only personal detail crossing between them is the requester's name — which the payer needs in
 * order to know who is asking — and the account number the payer already has.
 */
@Schema(name = "MoneyRequestResponse")
public record MoneyRequestResponse(
        UUID id,
        @Schema(description = "OUTGOING when you raised it, INCOMING when you are being asked")
                String direction,
        @Schema(example = "Ayubkhon Salimov") String requesterName,
        @Schema(description = "Where the money would land", example = "MB-482910375512")
                String requesterAccountNumber,
        @Schema(description = "The account being asked", example = "MB-119384756201")
                String payerAccountNumber,
        BigDecimal amount,
        CurrencyCode currency,
        String description,
        @Schema(description = "Reflects expiry even before any job has run") MoneyRequestStatus status,
        Instant expiresAt,
        @Schema(description = "The transfer that settled it, when it was paid") UUID settledTransferId,
        Instant settledAt,
        Instant createdAt) {

    public static MoneyRequestResponse from(MoneyRequest request, boolean outgoing, Instant now) {
        return new MoneyRequestResponse(
                request.getId(),
                outgoing ? "OUTGOING" : "INCOMING",
                request.getRequester().fullName(),
                request.getRequesterAccount().getAccountNumber(),
                request.getPayerAccountNumber(),
                request.getAmount(),
                request.getCurrency(),
                request.getDescription(),
                request.effectiveStatus(now),
                request.getExpiresAt(),
                request.getSettledTransfer() == null ? null : request.getSettledTransfer().getId(),
                request.getSettledAt(),
                request.getCreatedAt());
    }
}
