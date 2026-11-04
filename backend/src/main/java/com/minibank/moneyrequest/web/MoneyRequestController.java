package com.minibank.moneyrequest.web;

import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.moneyrequest.dto.MoneyRequestCreate;
import com.minibank.moneyrequest.dto.MoneyRequestResponse;
import com.minibank.moneyrequest.dto.PayMoneyRequest;
import com.minibank.moneyrequest.service.MoneyRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requesting money from somebody.
 *
 * <p>A request never moves money on its own: only the payer can settle one, and doing so creates an
 * ordinary transfer from the payer's account.
 */
@RestController
@RequestMapping("/api/v1/money-requests")
@Tag(name = "Money requests", description = "Ask to be paid; the payer settles with a real transfer")
public class MoneyRequestController {

    private final MoneyRequestService moneyRequestService;
    private final Clock clock;

    public MoneyRequestController(MoneyRequestService moneyRequestService, Clock clock) {
        this.moneyRequestService = moneyRequestService;
        this.clock = clock;
    }

    @GetMapping("/outgoing")
    @Operation(summary = "Requests I have raised")
    public ResponseEntity<List<MoneyRequestResponse>> outgoing() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(moneyRequestService.outgoing(caller.getId()).stream()
                .map(request -> MoneyRequestResponse.from(request, true, clock.instant()))
                .toList());
    }

    @GetMapping("/incoming")
    @Operation(summary = "Requests addressed to my accounts")
    public ResponseEntity<List<MoneyRequestResponse>> incoming() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(moneyRequestService.incoming(caller.getId()).stream()
                .map(request -> MoneyRequestResponse.from(request, false, clock.instant()))
                .toList());
    }

    @PostMapping
    @Operation(
            summary = "Ask somebody for money",
            description = "The payee account and currency are checked now, so an unpayable request is refused here.")
    @ApiResponse(responseCode = "201", description = "Request raised")
    public ResponseEntity<MoneyRequestResponse> create(@Valid @RequestBody MoneyRequestCreate command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MoneyRequestResponse.from(moneyRequestService.create(command, caller), true, clock.instant()));
    }

    @PostMapping("/{id}/pay")
    @Operation(
            summary = "Pay a request addressed to me",
            description = "Creates a transfer from your account. Your own limits and balance decide the outcome.")
    @ApiResponse(responseCode = "422", description = "Your account cannot cover it, or a limit was exceeded")
    public ResponseEntity<MoneyRequestResponse> pay(
            @PathVariable UUID id, @Valid @RequestBody PayMoneyRequest command) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                MoneyRequestResponse.from(moneyRequestService.pay(id, command, caller), false, clock.instant()));
    }

    @PatchMapping("/{id}/decline")
    @Operation(summary = "Decline a request addressed to me")
    public ResponseEntity<MoneyRequestResponse> decline(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                MoneyRequestResponse.from(moneyRequestService.decline(id, caller), false, clock.instant()));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Withdraw a request I raised")
    public ResponseEntity<MoneyRequestResponse> cancel(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                MoneyRequestResponse.from(moneyRequestService.cancel(id, caller), true, clock.instant()));
    }
}
