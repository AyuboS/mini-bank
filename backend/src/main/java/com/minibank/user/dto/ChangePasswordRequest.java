package com.minibank.user.dto;

import com.minibank.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Request to change one's own password. Requires proof of the current password. */
@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @StrongPassword @Schema(example = "N3w-Str0ng-Pass!") String newPassword) {}
