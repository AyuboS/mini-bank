package com.minibank.card.repository;

import com.minibank.card.domain.Card;
import com.minibank.card.domain.CardStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, UUID> {

    /**
     * The owner's cards, newest first.
     *
     * <p>Account and owner are fetched in the same query. Without that, rendering a wallet of six
     * cards would issue six extra selects for the account each card is attached to — the textbook
     * N+1, and one that only shows up once somebody has more than one card.
     */
    @Query(
            """
            SELECT c FROM Card c
            JOIN FETCH c.account
            JOIN FETCH c.owner
            WHERE c.owner.id = :ownerId AND c.status <> :excluded
            ORDER BY c.createdAt DESC
            """)
    List<Card> findAllOwnedBy(@Param("ownerId") UUID ownerId, @Param("excluded") CardStatus excluded);

    @Query(
            """
            SELECT c FROM Card c
            JOIN FETCH c.account
            JOIN FETCH c.owner
            WHERE c.id = :id
            """)
    Optional<Card> findByIdWithRelations(@Param("id") UUID id);

    long countByOwnerIdAndStatusNot(UUID ownerId, CardStatus status);

    boolean existsByPanFingerprint(String panFingerprint);
}
