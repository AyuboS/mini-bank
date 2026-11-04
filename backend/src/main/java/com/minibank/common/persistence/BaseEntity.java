package com.minibank.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Identity and creation timestamp shared by every persisted entity.
 *
 * <p>Primary keys are random UUIDs rather than sequences: account and transfer identifiers appear
 * in URLs, and a sequential key would let one customer probe another customer's resources simply by
 * decrementing a number.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Identity is based on the primary key only, and an entity that has not been persisted yet is
     * equal only to itself. Proxies are unwrapped so that a lazily loaded reference compares equal
     * to the entity it stands for.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity otherEntity)) {
            return false;
        }
        if (!effectiveClass(this).equals(effectiveClass(otherEntity))) {
            return false;
        }
        UUID thisId = this.getId();
        return thisId != null && thisId.equals(otherEntity.getId());
    }

    @Override
    public final int hashCode() {
        // A constant keeps the contract intact while the id is still null before the first flush.
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object candidate) {
        return candidate instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : candidate.getClass();
    }
}
