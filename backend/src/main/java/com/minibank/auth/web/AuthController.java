package com.minibank.auth.web;

import com.minibank.auth.dto.AuthenticationResponse;
import com.minibank.auth.dto.EmailVerificationRequest;
import com.minibank.auth.dto.LoginRequest;
import com.minibank.auth.dto.PhoneCodeRequest;
import com.minibank.auth.dto.PhoneCodeVerificationRequest;
import com.minibank.auth.dto.RefreshTokenRequest;
import com.minibank.auth.dto.RegisterRequest;
import com.minibank.auth.service.AuthService;
import com.minibank.common.error.ApiError;
import com.minibank.user.dto.UserResponse;
import com.minibank.verification.dto.VerificationChallenge;
import com.minibank.verification.dto.VerificationTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login and token lifecycle")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a new customer",
            description = "Creates an ACTIVE customer and opens their first EUR account. "
                    + "The role is always CUSTOMER; it cannot be influenced by the request.")
    @ApiResponse(responseCode = "201", description = "Customer created")
    @ApiResponse(
            responseCode = "400",
            description = "Validation failed, for example a password that does not meet the policy",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Username or email already registered",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate and obtain tokens",
            description = "Returns a short lived access token and a rotating refresh token. "
                    + "Repeated failures lock the account temporarily.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "423",
            description = "Account locked after repeated failures",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotate tokens",
            description = "Exchanges a refresh token for a new pair. The presented token is invalidated. "
                    + "Presenting an already-rotated token revokes the whole session family.")
    @ApiResponse(responseCode = "200", description = "New token pair issued")
    @ApiResponse(
            responseCode = "401",
            description = "Refresh token unknown, expired or already used",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<AuthenticationResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/phone/code")
    @Operation(
            summary = "Send a one-time code by SMS",
            description = "Always answers 202 with the same body, whether or not the number belongs to "
                    + "an account: any difference would let a caller test who banks here. "
                    + "A resend before the cooldown elapses is refused.")
    @ApiResponse(responseCode = "202", description = "Request accepted; a code may have been sent")
    @ApiResponse(
            responseCode = "409",
            description = "Registration requested for a number that already has an account",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "429",
            description = "A code was sent too recently",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<VerificationChallenge> sendPhoneCode(@Valid @RequestBody PhoneCodeRequest request) {
        VerificationChallenge challenge = authService.requestPhoneCode(request.phoneNumber(), request.purpose());
        return ResponseEntity.accepted().body(challenge);
    }

    @PostMapping("/phone/login")
    @Operation(
            summary = "Sign in with a phone number",
            description = "Exchanges a code sent for the LOGIN purpose for a token pair. "
                    + "The number must belong to an account that has verified it.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(
            responseCode = "400",
            description = "The code is incorrect",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "410",
            description = "The code has expired",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Too many attempts; request a new code",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<AuthenticationResponse> loginWithPhone(
            @Valid @RequestBody PhoneCodeVerificationRequest request) {
        return ResponseEntity.ok(authService.loginWithPhone(request.phoneNumber(), request.code()));
    }

    @PostMapping("/phone/confirm")
    @Operation(
            summary = "Confirm a number before registering",
            description = "Checks a code sent for the REGISTRATION purpose and returns a short lived "
                    + "token. Pass that token as verificationToken when calling /register.")
    @ApiResponse(responseCode = "200", description = "Number confirmed")
    public ResponseEntity<VerificationTicket> confirmPhone(
            @Valid @RequestBody PhoneCodeVerificationRequest request) {
        return ResponseEntity.ok(authService.confirmPhoneNumber(request.phoneNumber(), request.code()));
    }

    @PostMapping("/email/verify")
    @Operation(
            summary = "Verify an email address",
            description = "Redeems the token from a verification link. The link works once.")
    @ApiResponse(responseCode = "200", description = "Address verified")
    @ApiResponse(
            responseCode = "410",
            description = "The link has expired",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UserResponse> verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.token()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out",
            description = "Revokes the presented refresh token and every token issued from the same login. "
                    + "Always returns 204, so it cannot be used to probe whether a token exists.")
    @ApiResponse(responseCode = "204", description = "Session revoked")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
