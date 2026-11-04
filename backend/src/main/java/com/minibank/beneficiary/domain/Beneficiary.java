package com.minibank.beneficiary.domain;

import com.minibank.common.persistence.AuditedEntity;
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
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * An account number a customer has saved as a payee.
 *
 * <p>Stores the destination account number rather than a foreign key to the account: a beneficiary
 * is a customer's own address-book entry, and it should survive the payee closing that account
 * exactly as a written-down number would.
 */
@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
public class Beneficiary extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "beneficiary_account_number", nullable = false, length = 20, updatable = false)
    private String beneficiaryAccountNumber;

    @Column(name = "nickname", nullable = false, length = 60)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BeneficiaryStatus status = BeneficiaryStatus.ACTIVE;

    /**
     * Pinned by the customer.
     *
     * <p>A flag rather than a separate table: being a favourite is a property of the saved payee,
     * and the ordering it implies -- favourites first, then most recently added -- is one index away.
     */
    @Column(name = "favourite", nullable = false)
    private boolean favourite;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Beneficiary() {
        // for JPA
    }

    public static Beneficiary create(User owner, String accountNumber, String nickname) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.owner = owner;
        beneficiary.beneficiaryAccountNumber = accountNumber;
        beneficiary.nickname = nickname;
        beneficiary.status = BeneficiaryStatus.ACTIVE;
        return beneficiary;
    }

    public void toggleFavourite() {
        favourite = !favourite;
    }

    public void softDelete() {
        status = BeneficiaryStatus.DELETED;
    }

    public boolean isOwnedBy(java.util.UUID userId) {
        return owner.getId().equals(userId);
    }

    /** True when this payee was added within the given window, used by the risk engine. */
    public boolean isNewerThan(Instant threshold) {
        return getCreatedAt() != null && getCreatedAt().isAfter(threshold);
    }
}
