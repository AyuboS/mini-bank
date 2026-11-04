package com.minibank.budget.repository;

import com.minibank.budget.domain.Budget;
import com.minibank.common.money.CurrencyCode;
import com.minibank.transfer.domain.TransactionCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @Query("SELECT b FROM Budget b WHERE b.owner.id = :ownerId AND b.currency = :currency ORDER BY b.category")
    List<Budget> findAllOwnedBy(@Param("ownerId") UUID ownerId, @Param("currency") CurrencyCode currency);

    Optional<Budget> findByOwnerIdAndCategoryAndCurrency(
            UUID ownerId, TransactionCategory category, CurrencyCode currency);

    Optional<Budget> findByIdAndOwnerId(UUID id, UUID ownerId);
}
