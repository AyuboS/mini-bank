package com.minibank.notification.center.domain;

import com.minibank.common.persistence.BaseEntity;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * One entry in a customer's notification feed.
 *
 * <p>Extends {@link BaseEntity} rather than {@code AuditedEntity} deliberately: a notification is a
 * statement about something that happened, and a record of the past that can be edited is not a
 * record. The only mutation the class permits is marking it read.
 */
@Entity
@Table(name = "notifications")
@Getter
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20, updatable = false)
    private NotificationSeverity severity;

    @Column(name = "title", nullable = false, length = 120, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 400, updatable = false)
    private String body;

    /** What this is about, for deep-linking. Not a foreign key; see the migration for why. */
    @Column(name = "entity_type", length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // for JPA
    }

    public static Notification of(
            User user,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String body,
            String entityType,
            UUID entityId) {
        Notification notification = new Notification();
        notification.user = user;
        notification.type = type;
        notification.severity = severity;
        notification.title = truncate(title, 120);
        notification.body = truncate(body, 400);
        notification.entityType = entityType;
        notification.entityId = entityId;
        return notification;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /**
     * Marks the notification read.
     *
     * <p>Idempotent, and it keeps the first timestamp: "when did you see this" has one answer, and
     * a second call must not move it.
     */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    /**
     * Trims to the column width rather than letting the insert fail.
     *
     * <p>A notification is a courtesy. Losing the tail of a long merchant name is a far better
     * outcome than a database constraint aborting the transaction that moved somebody's money.
     */
    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
