package com.minibank.notification.center.dto;

import com.minibank.notification.center.domain.Notification;
import com.minibank.notification.center.domain.NotificationSeverity;
import com.minibank.notification.center.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One feed entry as the client sees it. */
@Schema(name = "NotificationResponse")
public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String body,
        @Schema(description = "What the notification is about, for deep-linking") String entityType,
        UUID entityId,
        boolean read,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getSeverity(),
                notification.getTitle(),
                notification.getBody(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
