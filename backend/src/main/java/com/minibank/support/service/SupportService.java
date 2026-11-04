package com.minibank.support.service;

import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.support.domain.SupportMessage;
import com.minibank.support.domain.SupportTicket;
import com.minibank.support.dto.SupportMessageRequest;
import com.minibank.support.dto.SupportTicketRequest;
import com.minibank.support.repository.SupportMessageRepository;
import com.minibank.support.repository.SupportTicketRepository;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support tickets raised by customers.
 *
 * <p>This is a real queue, not a simulated assistant: a ticket is stored, given a reference the
 * customer can quote, and left for a human. Nothing here generates a reply. An automated
 * "we understand your concern" would be worse than silence, because it would make a customer with
 * an urgent problem believe they had been heard.
 */
@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    /** No I, O, 0 or 1: a reference gets read down a phone. Same alphabet as transfer references. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int REFERENCE_LENGTH = 6;

    /** Enough attempts at a free reference that exhausting them means something else is wrong. */
    private static final int REFERENCE_ATTEMPTS = 5;

    private final SupportTicketRepository tickets;
    private final SupportMessageRepository messages;
    private final UserRepository users;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public SupportService(
            SupportTicketRepository tickets,
            SupportMessageRepository messages,
            UserRepository users,
            AuditService auditService) {
        this.tickets = tickets;
        this.messages = messages;
        this.users = users;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> list(UUID ownerId) {
        return tickets.findAllOwnedBy(ownerId);
    }

    @Transactional(readOnly = true)
    public SupportTicket get(UUID ticketId, AuthenticatedUser caller) {
        return requireOwnTicket(ticketId, caller);
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> thread(UUID ticketId, AuthenticatedUser caller) {
        requireOwnTicket(ticketId, caller);
        return messages.findThread(ticketId);
    }

    /** Opens a ticket, with the customer's description as its first message. */
    @Transactional
    public SupportTicket open(SupportTicketRequest request, AuthenticatedUser caller) {
        User owner = users.findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        SupportTicket ticket = tickets.save(SupportTicket.open(
                owner, generateReference(), request.subject().trim(), request.category()));
        messages.save(SupportMessage.fromCustomer(ticket, owner, request.description()));

        auditService.record(AuditEvent.success(AuditAction.SUPPORT_TICKET_OPENED)
                .by(owner)
                .on("SupportTicket", ticket.getId())
                .with("reference", ticket.getReference())
                .with("category", request.category()));
        log.info("Support ticket {} opened", ticket.getReference());
        return ticket;
    }

    /** Adds the customer's reply, reopening a resolved ticket. */
    @Transactional
    public SupportTicket reply(UUID ticketId, SupportMessageRequest request, AuthenticatedUser caller) {
        SupportTicket ticket = requireOwnTicket(ticketId, caller);
        ticket.customerReplied();

        User author = users.findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        messages.save(SupportMessage.fromCustomer(ticket, author, request.body()));

        auditService.record(AuditEvent.success(AuditAction.SUPPORT_TICKET_REPLIED)
                .by(author)
                .on("SupportTicket", ticketId));
        return ticket;
    }

    /**
     * The customer closes their own ticket.
     *
     * <p>Only the customer can do this. A queue that lets the bank close a ticket the customer still
     * considers open is how a complaint disappears.
     */
    @Transactional
    public SupportTicket close(UUID ticketId, AuthenticatedUser caller) {
        SupportTicket ticket = requireOwnTicket(ticketId, caller);
        ticket.close();
        auditService.record(AuditEvent.success(AuditAction.SUPPORT_TICKET_CLOSED)
                .by(caller.getId(), caller.getUsername())
                .on("SupportTicket", ticketId));
        return ticket;
    }

    private SupportTicket requireOwnTicket(UUID ticketId, AuthenticatedUser caller) {
        // Scoped by owner in the query itself: somebody else's ticket is simply not found, so the
        // endpoint cannot be used to discover which references exist.
        return tickets
                .findByIdAndOwnerId(ticketId, caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.SUPPORT_TICKET_NOT_FOUND));
    }

    /**
     * A free reference such as {@code MB-SUP-7K3M2A}.
     *
     * <p>Checked against the table before use, and backed by a unique constraint if two requests
     * race. With 32^6 possibilities a collision is remote; "remote" is not "impossible", which is
     * why both the check and the constraint exist.
     */
    private String generateReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            StringBuilder builder = new StringBuilder("MB-SUP-");
            for (int i = 0; i < REFERENCE_LENGTH; i++) {
                builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            String candidate = builder.toString();
            if (!tickets.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a free support reference");
    }
}
