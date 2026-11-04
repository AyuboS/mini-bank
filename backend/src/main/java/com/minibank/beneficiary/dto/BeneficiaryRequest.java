package com.minibank.beneficiary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request to save a payee. */
@Schema(name = "BeneficiaryRequest")
public record BeneficiaryRequest(
        @NotBlank
                @Pattern(regexp = "^MB-[0-9]{12}$", message = "must be a valid MBank account number")
                @Schema(example = "MB-482910375512")
                String accountNumber,
        @NotBlank @Size(min = 1, max = 60) @Schema(example = "Landlord") String nickname) {}
