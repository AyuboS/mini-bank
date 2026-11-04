package com.minibank.idempotency.domain;

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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * The record of one idempotent operation.
 *
 * <p>{@code requestHash} is what makes the key safe to trust: replaying a key with a different body
 * is a client bug (or an attack), and returning the first request's result for the second request's
 * payload would be worse than either succeeding or failing. Such a replay is rejected instead.
 *
 * <p>The unique constraint on (user, endpoint, key) is the concurrency control. Two simultaneous
 * requests with the same key race to insert; exactly one wins, and the loser sees IN_PROGRESS.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
public class IdempotencyRecord extends AuditedEntity {

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "endpoint", nullable = false, length = 120, updatable = false)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IdempotencyRecord() {
        // for JPA
    }

    public static IdempotencyRecord reserve(String key, User user, String endpoint, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.idempotencyKey = key;
        record.user = user;
        record.endpoint = endpoint;
        record.requestHash = requestHash;
        record.status = IdempotencyStatus.IN_PROGRESS;
        return record;
    }

    public void complete(int responseStatus, String responseBody, UUID resourceId) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.resourceId = resourceId;
    }

    public void fail(int responseStatus, String responseBody) {
        this.status = IdempotencyStatus.FAILED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public boolean matches(String candidateRequestHash) {
        return requestHash.equals(candidateRequestHash);
    }
}
