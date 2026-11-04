package com.minibank.budget.domain;

import com.minibank.common.money.CurrencyCode;
import com.minibank.common.persistence.AuditedEntity;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A monthly cap on one spending category.
 *
 * <p>Holds the limit and nothing else. How much has been spent against it is computed from the
 * customer's transactions at read time, so a recategorised payment is reflected immediately and
 * there is no counter that can fall out of step with the ledger.
 */
@Entity
@Table(name = "budgets")
@Getter
@Setter
public class Budget extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20, updatable = false)
    private TransactionCategory category;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private CurrencyCode currency;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Budget() {
        // for JPA
    }

    public static Budget of(User owner, TransactionCategory category, BigDecimal amount, CurrencyCode currency) {
        Budget budget = new Budget();
        budget.owner = owner;
        budget.category = category;
        budget.amount = amount;
        budget.currency = currency;
        return budget;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }
}
