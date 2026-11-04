package com.minibank.notification.center.repository;

import com.minibank.notification.center.domain.Notification;
import com.minibank.notification.center.domain.NotificationType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId ORDER BY n.createdAt DESC")
    Page<Notification> findFeed(@Param("userId") UUID userId, Pageable pageable);

    @Query(
            """
            SELECT n FROM Notification n
            WHERE n.user.id = :userId AND n.type = :type
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findFeedByType(
            @Param("userId") UUID userId, @Param("type") NotificationType type, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.readAt IS NULL ORDER BY n.createdAt DESC")
    Page<Notification> findUnread(@Param("userId") UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Marks everything unread as read in one statement.
     *
     * <p>A read-modify-write loop over the feed would issue one UPDATE per row and, worse, would
     * race with notifications arriving mid-loop. The {@code readAt IS NULL} predicate also makes
     * this naturally idempotent: a second call touches nothing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
