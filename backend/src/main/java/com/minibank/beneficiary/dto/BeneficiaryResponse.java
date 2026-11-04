package com.minibank.beneficiary.dto;

import com.minibank.beneficiary.domain.Beneficiary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A saved payee.
 *
 * <p>{@code holderName} is the payee's display name as confirmed at the time the beneficiary was
 * listed. It is the payee's first name and last initial only: enough for the payer to check they
 * are about to pay the right person, without turning the beneficiary list into a directory that
 * reveals full identities for any account number a customer cares to add.
 */
@Schema(name = "BeneficiaryResponse")
public record BeneficiaryResponse(
        UUID id,
        @Schema(example = "MB-482910375512") String accountNumber,
        @Schema(example = "Landlord") String nickname,
        @Schema(example = "Jane S.") String holderName,
        @Schema(description = "Pinned by the customer; favourites sort first") boolean favourite,
        Instant createdAt) {

    public static BeneficiaryResponse from(Beneficiary beneficiary, String holderName) {
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getBeneficiaryAccountNumber(),
                beneficiary.getNickname(),
                holderName,
                beneficiary.isFavourite(),
                beneficiary.getCreatedAt());
    }
}
