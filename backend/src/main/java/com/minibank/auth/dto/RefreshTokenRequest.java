package com.minibank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Carries a refresh token for rotation or revocation. */
@Schema(name = "RefreshTokenRequest")
public record RefreshTokenRequest(@NotBlank @Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}
