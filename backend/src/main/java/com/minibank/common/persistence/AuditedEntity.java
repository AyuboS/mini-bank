package com.minibank.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * Base class for entities that may change after creation and therefore track a modification time.
 *
 * <p>Append-only records such as ledger entries and audit logs deliberately extend {@link
 * BaseEntity} instead, so that "this row was updated" is not even representable for them.
 */
@MappedSuperclass
public abstract class AuditedEntity extends BaseEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
