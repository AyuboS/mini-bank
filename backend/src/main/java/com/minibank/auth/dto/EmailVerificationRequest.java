package com.minibank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Redeems the token from a verification link. */
@Schema(name = "EmailVerificationRequest")
public record EmailVerificationRequest(@NotBlank @Size(max = 200) String token) {

    @Override
    public String toString() {
        return "EmailVerificationRequest[token=***]";
    }
}
