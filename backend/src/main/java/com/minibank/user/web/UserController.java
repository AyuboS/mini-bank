package com.minibank.user.web;

import com.minibank.common.error.ApiError;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.user.dto.ChangePasswordRequest;
import com.minibank.user.dto.UpdateProfileRequest;
import com.minibank.user.dto.UserResponse;
import com.minibank.user.service.UserService;
import com.minibank.verification.domain.VerificationPurpose;
import com.minibank.verification.dto.VerificationChallenge;
import com.minibank.verification.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile endpoints.
 *
 * <p>Every route resolves the target user from the security context, never from a path variable, so
 * there is no identifier a caller could substitute to reach somebody else's profile.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Profile", description = "The authenticated user's own profile")
public class UserController {

    private final UserService userService;
    private final VerificationService verificationService;

    public UserController(UserService userService, VerificationService verificationService) {
        this.userService = userService;
        this.verificationService = verificationService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get my profile")
    public ResponseEntity<UserResponse> me() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(UserResponse.from(userService.getById(caller.getId())));
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Update my profile",
            description = "Only name and phone number can be changed. Role, status and credentials are "
                    + "not part of this request shape and cannot be modified here.")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(UserResponse.from(userService.updateProfile(caller.getId(), request)));
    }

    @PostMapping("/me/phone/code")
    @Operation(
            summary = "Send a code to confirm my phone number",
            description = "Confirms the number already on the profile. A verified number becomes a "
                    + "sign-in credential, which is why it needs proving rather than just typing.")
    @ApiResponse(responseCode = "202", description = "Code sent")
    public ResponseEntity<VerificationChallenge> sendPhoneCode() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        var user = userService.getById(caller.getId());
        return ResponseEntity.accepted()
                .body(verificationService.requestPhoneCode(
                        user.getPhoneNumber(), VerificationPurpose.CONTACT_VERIFICATION, user));
    }

    @PostMapping("/me/phone/confirm")
    @Operation(summary = "Confirm my phone number with the code")
    public ResponseEntity<UserResponse> confirmPhone(@Valid @RequestBody ConfirmPhoneRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(UserResponse.from(userService.confirmPhone(caller.getId(), request.code())));
    }

    @PostMapping("/me/email/verify")
    @Operation(
            summary = "Send me a verification email",
            description = "Does nothing if the address is already verified, so a stray click cannot "
                    + "invalidate a link that is about to be used.")
    @ApiResponse(responseCode = "202", description = "Email sent, if one was needed")
    public ResponseEntity<VerificationChallenge> resendEmailVerification() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.accepted()
                .body(verificationService.requestEmailVerification(userService.getById(caller.getId())));
    }

    /** The code from the SMS, for confirming a number already on the profile. */
    @Schema(name = "ConfirmPhoneRequest")
    public record ConfirmPhoneRequest(
            @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Pattern(regexp = "^[0-9]{4,8}$")
                    String code) {

        @Override
        public String toString() {
            return "ConfirmPhoneRequest[code=***]";
        }
    }

    @PostMapping("/me/password")
    @Operation(
            summary = "Change my password",
            description = "Requires the current password. All existing sessions are revoked on success.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(
            responseCode = "400",
            description = "Current password incorrect, or new password fails the policy",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        userService.changePassword(caller.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
