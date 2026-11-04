package com.minibank.limit.domain;

import com.minibank.common.persistence.AuditedEntity;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-customer limit overrides set by an administrator.
 *
 * <p>Every field is nullable and a null one falls back to the platform default, so an administrator
 * can raise a single customer's daily ceiling without having to restate the other limits.
 */
@Entity
@Table(name = "transfer_limit_overrides")
@Getter
@Setter
public class TransferLimitOverride extends AuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "per_transfer_max", precision = 19, scale = 4)
    private BigDecimal perTransferMax;

    @Column(name = "daily_max", precision = 19, scale = 4)
    private BigDecimal dailyMax;

    @Column(name = "max_transfers_per_hour")
    private Integer maxTransfersPerHour;

    @Column(name = "note", length = 255)
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TransferLimitOverride() {
        // for JPA
    }

    public static TransferLimitOverride forUser(User user) {
        TransferLimitOverride override = new TransferLimitOverride();
        override.user = user;
        return override;
    }
}
