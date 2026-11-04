package com.minibank.transfer.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.risk.dto.RiskAssessmentResponse;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.domain.TransferType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A transfer as returned by the API. */
@Schema(name = "TransferResponse")
public record TransferResponse(
        UUID id,
        @Schema(example = "TRX-2026-A7F3K92M") String reference,
        TransferType type,
        TransferStatus status,
        @Schema(example = "MB-482910375512") String fromAccountNumber,
        @Schema(example = "MB-119384756201") String toAccountNumber,
        @Schema(example = "100.5000") BigDecimal amount,
        CurrencyCode currency,
        String description,
        @Schema(description = "Null when the payment could not be placed confidently")
                TransactionCategory category,
        @Schema(description = "Present when the transfer did not settle") String failureReason,
        @Schema(description = "Present when the risk engine produced a non-trivial verdict")
                RiskAssessmentResponse risk,
        Instant createdAt,
        Instant completedAt) {

    /**
     * Maps a transfer, reconstructing the risk verdict from what was stored against it.
     *
     * <p>Used by every listing. Without this, only the response to the request that created a
     * transfer would carry its risk outcome, and an administrator opening a held transfer later
     * would see no explanation for the hold.
     */
    public static TransferResponse from(Transfer transfer) {
        RiskAssessmentResponse risk = transfer.getRiskDecision() == null
                ? null
                : new RiskAssessmentResponse(
                        com.minibank.risk.domain.RiskDecision.valueOf(transfer.getRiskDecision()),
                        transfer.getRiskScore() == null ? 0 : transfer.getRiskScore(),
                        transfer.getRiskReasons() == null ? java.util.List.of() : transfer.getRiskReasons());
        return from(transfer, risk);
    }

    public static TransferResponse from(Transfer transfer, RiskAssessmentResponse risk) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getTransferType(),
                transfer.getStatus(),
                transfer.getSourceAccount().getAccountNumber(),
                transfer.getDestinationAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCategory(),
                transfer.getFailureReason(),
                risk,
                transfer.getCreatedAt(),
                transfer.getCompletedAt());
    }
}
