package com.minibank.auth.dto;

import com.minibank.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Tokens issued after a successful login or refresh. */
@Schema(name = "AuthenticationResponse")
public record AuthenticationResponse(
        @Schema(description = "Short lived JWT for the Authorization header") String accessToken,
        @Schema(description = "Opaque token used to obtain a new access token") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "When the access token stops being accepted") Instant accessTokenExpiresAt,
        @Schema(description = "When the refresh token stops being accepted") Instant refreshTokenExpiresAt,
        UserResponse user) {

    public static final String TOKEN_TYPE = "Bearer";

    @Override
    public String toString() {
        return "AuthenticationResponse[user=" + (user == null ? null : user.username()) + ", tokens=***]";
    }
}
