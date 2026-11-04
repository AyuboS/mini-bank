package com.minibank.support.repository;

import com.minibank.support.domain.SupportMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    /** A thread, oldest first, with authors joined: a conversation is read forwards. */
    @Query(
            """
            SELECT m FROM SupportMessage m
            LEFT JOIN FETCH m.author
            WHERE m.ticket.id = :ticketId
            ORDER BY m.createdAt
            """)
    List<SupportMessage> findThread(@Param("ticketId") UUID ticketId);
}
