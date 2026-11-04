package com.minibank.support.domain;

import com.minibank.common.persistence.BaseEntity;
import com.minibank.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One message in a ticket thread.
 *
 * <p>Extends {@link BaseEntity}, not {@code AuditedEntity}, and has no setters: an edited support
 * message is not a support message. The thread is append-only by construction rather than by
 * convention.
 */
@Entity
@Table(name = "support_messages")
@Getter
public class SupportMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private SupportTicket ticket;

    /** Null when the message came from the bank rather than from a person's account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", updatable = false)
    private User author;

    @Column(name = "from_staff", nullable = false, updatable = false)
    private boolean fromStaff;

    @Column(name = "body", nullable = false, length = 4000, updatable = false)
    private String body;

    protected SupportMessage() {
        // for JPA
    }

    public static SupportMessage fromCustomer(SupportTicket ticket, User author, String body) {
        SupportMessage message = new SupportMessage();
        message.ticket = ticket;
        message.author = author;
        message.fromStaff = false;
        message.body = body;
        return message;
    }

    public static SupportMessage fromBank(SupportTicket ticket, User staff, String body) {
        SupportMessage message = new SupportMessage();
        message.ticket = ticket;
        message.author = staff;
        message.fromStaff = true;
        message.body = body;
        return message;
    }
}
