package com.minibank.limit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Administrative limit override. A null field means "fall back to the platform default". */
@Schema(name = "UpdateTransferLimitsRequest", description = "Null fields revert to the platform default")
public record UpdateTransferLimitsRequest(
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal perTransferMax,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal dailyMax,
        @Min(1) Integer maxTransfersPerHour,
        @Size(max = 255) @Schema(example = "Verified business customer") String note) {}
