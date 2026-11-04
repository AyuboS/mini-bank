package com.minibank.scheduling.web;

import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.scheduling.dto.ScheduledTransferRequest;
import com.minibank.scheduling.dto.ScheduledTransferResponse;
import com.minibank.scheduling.service.ScheduledTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standing orders: scheduled and recurring payments.
 *
 * <p>Due orders are executed by a background runner through the ordinary transfer path, so a
 * scheduled payment carries the same limits, risk checks and ledger guarantees as a manual one.
 */
@RestController
@RequestMapping("/api/v1/scheduled-transfers")
@Tag(name = "Scheduled transfers", description = "Payments that run later, and recurring payments")
public class ScheduledTransferController {

    private final ScheduledTransferService scheduledTransferService;

    public ScheduledTransferController(ScheduledTransferService scheduledTransferService) {
        this.scheduledTransferService = scheduledTransferService;
    }

    @GetMapping
    @Operation(summary = "My standing orders")
    public ResponseEntity<List<ScheduledTransferResponse>> list() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(scheduledTransferService.list(caller.getId()).stream()
                .map(ScheduledTransferResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One standing order")
    public ResponseEntity<ScheduledTransferResponse> get(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(ScheduledTransferResponse.from(scheduledTransferService.get(id, caller)));
    }

    @PostMapping
    @Operation(
            summary = "Set up a standing order",
            description = "The payee and currency are validated now, so a bad order fails here rather than at 03:00.")
    @ApiResponse(responseCode = "201", description = "Order created")
    public ResponseEntity<ScheduledTransferResponse> create(@Valid @RequestBody ScheduledTransferRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ScheduledTransferResponse.from(scheduledTransferService.create(request, caller)));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Change amount, description or schedule",
            description = "The source and payee cannot be changed; set up a new order instead.")
    public ResponseEntity<ScheduledTransferResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ScheduledTransferRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                ScheduledTransferResponse.from(scheduledTransferService.update(id, request, caller)));
    }

    @PatchMapping("/{id}/pause")
    @Operation(summary = "Pause an order")
    public ResponseEntity<ScheduledTransferResponse> pause(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(ScheduledTransferResponse.from(scheduledTransferService.pause(id, caller)));
    }

    @PatchMapping("/{id}/resume")
    @Operation(
            summary = "Resume a paused order",
            description = "Dates missed while paused are skipped; resuming never fires a catch-up run.")
    public ResponseEntity<ScheduledTransferResponse> resume(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(ScheduledTransferResponse.from(scheduledTransferService.resume(id, caller)));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order for good")
    public ResponseEntity<ScheduledTransferResponse> cancel(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(ScheduledTransferResponse.from(scheduledTransferService.cancel(id, caller)));
    }
}
