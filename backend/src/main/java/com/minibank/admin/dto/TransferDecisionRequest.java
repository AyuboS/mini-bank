package com.minibank.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason recorded against a manual transfer decision. Required, so the audit trail is meaningful. */
@Schema(name = "TransferDecisionRequest")
public record TransferDecisionRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Confirmed with customer by phone") String reason) {}
