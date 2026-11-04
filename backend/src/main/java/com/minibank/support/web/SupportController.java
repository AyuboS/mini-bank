package com.minibank.support.web;

import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.support.dto.SupportMessageRequest;
import com.minibank.support.dto.SupportTicketRequest;
import com.minibank.support.dto.SupportTicketResponse;
import com.minibank.support.service.SupportService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's support tickets.
 *
 * <p>Tickets go to a human queue. Nothing here generates a reply, and the API does not pretend
 * otherwise.
 */
@RestController
@RequestMapping("/api/v1/support/tickets")
@Tag(name = "Support", description = "Raise and follow up support tickets")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    @Operation(summary = "My tickets, most recently updated first")
    public ResponseEntity<List<SupportTicketResponse>> list() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(supportService.list(caller.getId()).stream()
                .map(SupportTicketResponse::summary)
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One ticket, with its full thread")
    public ResponseEntity<SupportTicketResponse> get(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(SupportTicketResponse.from(
                supportService.get(id, caller), supportService.thread(id, caller)));
    }

    @PostMapping
    @Operation(summary = "Open a ticket", description = "You get a reference to quote. A person answers it.")
    @ApiResponse(responseCode = "201", description = "Ticket opened")
    public ResponseEntity<SupportTicketResponse> open(@Valid @RequestBody SupportTicketRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SupportTicketResponse.summary(supportService.open(request, caller)));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Reply on a ticket", description = "Replying to a resolved ticket reopens it.")
    @ApiResponse(responseCode = "409", description = "The ticket is closed; open a new one")
    public ResponseEntity<SupportTicketResponse> reply(
            @PathVariable UUID id, @Valid @RequestBody SupportMessageRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        supportService.reply(id, request, caller);
        return ResponseEntity.ok(SupportTicketResponse.from(
                supportService.get(id, caller), supportService.thread(id, caller)));
    }

    @PatchMapping("/{id}/close")
    @Operation(summary = "Close my ticket")
    public ResponseEntity<SupportTicketResponse> close(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(SupportTicketResponse.summary(supportService.close(id, caller)));
    }
}
