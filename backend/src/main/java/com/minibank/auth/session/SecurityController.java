package com.minibank.auth.session;

import com.minibank.auth.dto.PasswordResetCompletion;
import com.minibank.auth.dto.PasswordResetRequest;
import com.minibank.auth.dto.PasswordResetVerification;
import com.minibank.auth.service.PasswordResetService;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.verification.dto.VerificationChallenge;
import com.minibank.verification.dto.VerificationTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The security centre: sessions, sign-in history, and the forgotten-password flow.
 *
 * <p>The three password-reset endpoints are public by necessity — somebody who cannot sign in
 * cannot present a token — and are registered as such in {@code SecurityConfig}. Each is rate
 * limited, and none of them reveals whether an account exists.
 */
@RestController
@RequestMapping("/api/v1/security")
@Tag(name = "Security", description = "Sessions, sign-in history and password recovery")
public class SecurityController {

    private final SessionService sessionService;
    private final PasswordResetService passwordResetService;

    public SecurityController(SessionService sessionService, PasswordResetService passwordResetService) {
        this.sessionService = sessionService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "My signed-in devices",
            description =
                    "One row per login. Pass your current refresh token to have this device marked; "
                            + "the list is correct without it.")
    public ResponseEntity<List<SessionResponse>> sessions(
            @RequestParam(required = false) String currentRefreshToken) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(sessionService.listSessions(caller.getId(), currentRefreshToken));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(
            summary = "End one session",
            description = "Revokes the whole token family server-side, so the next refresh from that device fails.")
    @ApiResponse(responseCode = "204", description = "Session ended")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        sessionService.revokeSession(id, caller);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    @Operation(
            summary = "Sign out every other device",
            description = "Leaves the session making the request signed in.")
    public ResponseEntity<Map<String, Integer>> revokeOthers(
            @RequestParam(required = false) String currentRefreshToken) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                Map.of("sessionsEnded", sessionService.revokeOtherSessions(caller, currentRefreshToken)));
    }

    @GetMapping("/login-history")
    @Operation(summary = "Recent sign-in activity, successful and not")
    public ResponseEntity<List<LoginHistoryEntry>> loginHistory() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(sessionService.loginHistory(caller.getId()));
    }

    // ------------------------------------------------------------- password reset

    @PostMapping("/password-reset/request")
    @Operation(
            summary = "Send a password reset code",
            description = "Answers identically whether or not the number is registered.")
    public ResponseEntity<VerificationChallenge> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.ok(passwordResetService.request(request));
    }

    @PostMapping("/password-reset/verify")
    @Operation(summary = "Exchange the code for a single-use ticket")
    @ApiResponse(responseCode = "400", description = "The code is wrong")
    @ApiResponse(responseCode = "429", description = "Too many attempts; request a new code")
    public ResponseEntity<VerificationTicket> verifyReset(@Valid @RequestBody PasswordResetVerification request) {
        return ResponseEntity.ok(passwordResetService.verify(request));
    }

    @PostMapping("/password-reset/complete")
    @Operation(
            summary = "Set the new password",
            description = "Burns the ticket and signs out every device.")
    @ApiResponse(responseCode = "204", description = "Password changed and all sessions ended")
    public ResponseEntity<Void> completeReset(@Valid @RequestBody PasswordResetCompletion request) {
        passwordResetService.complete(request);
        return ResponseEntity.noContent().build();
    }
}
