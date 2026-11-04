package com.minibank.notification.center.service;

import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.notification.center.domain.Notification;
import com.minibank.notification.center.domain.NotificationSeverity;
import com.minibank.notification.center.domain.NotificationType;
import com.minibank.notification.center.repository.NotificationRepository;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads the in-app notification feed.
 *
 * <h2>Why publishing never throws</h2>
 *
 * <p>{@link #publish} is called from inside transactions that are moving money. If writing a
 * notification could fail the caller, then a full disk, a constraint someone tightened, or a title
 * one character too long would roll back a settled payment. That trade is plainly wrong: the
 * payment is the product, the notification is a courtesy. Failures are therefore logged and
 * swallowed, and the log line is the alarm.
 *
 * <p>It still participates in the caller's transaction, so a notification cannot survive a payment
 * that rolled back. Courtesy, but not fiction.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    /** Publishes to a user already loaded by the caller. Preferred: it costs no extra query. */
    @Transactional
    public void publish(
            User user,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String body,
            String entityType,
            UUID entityId) {
        if (user == null) {
            return;
        }
        try {
            notifications.save(Notification.of(user, type, severity, title, body, entityType, entityId));
        } catch (RuntimeException ex) {
            log.error(
                    "Could not record a {} notification for user {}; the underlying action is unaffected",
                    type,
                    user.getId(),
                    ex);
        }
    }

    /** Publishes by user id, for callers that hold only the id. */
    @Transactional
    public void publish(
            UUID userId,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String body,
            String entityType,
            UUID entityId) {
        users.findById(userId)
                .ifPresent(user -> publish(user, type, severity, title, body, entityType, entityId));
    }

    @Transactional(readOnly = true)
    public Page<Notification> feed(UUID userId, NotificationType type, boolean unreadOnly, Pageable pageable) {
        if (unreadOnly) {
            return notifications.findUnread(userId, pageable);
        }
        return type == null
                ? notifications.findFeed(userId, pageable)
                : notifications.findFeedByType(userId, type, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /**
     * Marks one notification read.
     *
     * <p>Scoped by user id in the query itself, not checked afterwards: somebody else's
     * notification is simply not found, so the endpoint cannot be used to probe which ids exist.
     */
    @Transactional
    public Notification markRead(UUID notificationId, UUID userId) {
        Notification notification = notifications
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found"));
        notification.markRead(clock.instant());
        return notification;
    }

    /** @return how many were still unread */
    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, clock.instant());
    }
}
