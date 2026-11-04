package com.minibank.support.repository;

import com.minibank.support.domain.SupportTicket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    @Query("SELECT t FROM SupportTicket t WHERE t.owner.id = :ownerId ORDER BY t.updatedAt DESC")
    List<SupportTicket> findAllOwnedBy(@Param("ownerId") UUID ownerId);

    Optional<SupportTicket> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByReference(String reference);
}
