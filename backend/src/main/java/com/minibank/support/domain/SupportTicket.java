package com.minibank.support.domain;

import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
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
 * A support conversation.
 *
 * <p>Messages live in their own table rather than being appended into a text column, so authorship,
 * ordering and timestamps survive intact. During a dispute, "who said what, when" is the only thing
 * a ticket is worth, and a concatenated blob cannot answer it.
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
public class SupportTicket extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    /** What the customer quotes when they call. Unique, and short enough to read down a phone. */
    @Column(name = "reference", nullable = false, length = 20, updatable = false)
    private String reference;

    @Column(name = "subject", nullable = false, length = 140)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SupportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportStatus status = SupportStatus.OPEN;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SupportTicket() {
        // for JPA
    }

    public static SupportTicket open(User owner, String reference, String subject, SupportCategory category) {
        SupportTicket ticket = new SupportTicket();
        ticket.owner = owner;
        ticket.reference = reference;
        ticket.subject = subject;
        ticket.category = category;
        ticket.status = SupportStatus.OPEN;
        return ticket;
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }

    /**
     * Records that the customer has replied.
     *
     * <p>A reply to a resolved ticket reopens it. The customer disagreeing that their problem is
     * solved is exactly the case a support queue must not drop.
     */
    public void customerReplied() {
        requireOpenForReplies();
        status = SupportStatus.OPEN;
    }

    public void resolve() {
        requireOpenForReplies();
        status = SupportStatus.RESOLVED;
    }

    public void close() {
        status = SupportStatus.CLOSED;
    }

    /** @throws BusinessRuleException if the ticket has been closed for good */
    public void requireOpenForReplies() {
        if (!status.acceptsReplies()) {
            throw new BusinessRuleException(ErrorCode.SUPPORT_TICKET_CLOSED);
        }
    }
}
