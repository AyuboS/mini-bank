package com.minibank.notification.center.web;

import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.common.web.PageResponse;
import com.minibank.notification.center.domain.NotificationType;
import com.minibank.notification.center.dto.NotificationResponse;
import com.minibank.notification.center.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The notification centre. Every endpoint is scoped to the caller; there is no cross-user read. */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@Tag(name = "Notifications", description = "The customer's in-app feed")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "The caller's notifications, newest first")
    public ResponseEntity<PageResponse<NotificationResponse>> list(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(PageResponse.from(
                notificationService.feed(caller.getId(), type, unreadOnly, PageRequest.of(page, size)),
                NotificationResponse::from));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many notifications the caller has not read")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(Map.of("unread", notificationService.unreadCount(caller.getId())));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                NotificationResponse.from(notificationService.markRead(id, caller.getId())));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every unread notification as read")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(Map.of("marked", notificationService.markAllRead(caller.getId())));
    }
}
