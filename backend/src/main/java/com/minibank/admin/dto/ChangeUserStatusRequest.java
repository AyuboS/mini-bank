package com.minibank.admin.dto;

import com.minibank.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Administrative change to a user's status. */
@Schema(name = "ChangeUserStatusRequest")
public record ChangeUserStatusRequest(
        @NotNull @Schema(example = "SUSPENDED") UserStatus status,
        @Size(max = 255) @Schema(example = "Reported stolen device") String reason) {}
