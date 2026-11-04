package com.minibank.support.dto;

import com.minibank.support.domain.SupportCategory;
import com.minibank.support.domain.SupportMessage;
import com.minibank.support.domain.SupportStatus;
import com.minibank.support.domain.SupportTicket;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A ticket, with its thread when one was asked for. */
@Schema(name = "SupportTicketResponse")
public record SupportTicketResponse(
        UUID id,
        @Schema(example = "MB-SUP-7K3M2A") String reference,
        String subject,
        SupportCategory category,
        SupportStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Present only when a single ticket was requested") List<Message> messages) {

    /** One message in the thread. */
    @Schema(name = "SupportMessage")
    public record Message(
            UUID id,
            @Schema(description = "True when the bank wrote it") boolean fromStaff,
            @Schema(description = "Who wrote it. Bank replies are attributed to MBank Support.")
                    String authorName,
            String body,
            Instant createdAt) {

        public static Message from(SupportMessage message) {
            return new Message(
                    message.getId(),
                    message.isFromStaff(),
                    message.isFromStaff()
                            ? "MBank Support"
                            : message.getAuthor() == null ? "You" : message.getAuthor().fullName(),
                    message.getBody(),
                    message.getCreatedAt());
        }
    }

    public static SupportTicketResponse summary(SupportTicket ticket) {
        return from(ticket, null);
    }

    public static SupportTicketResponse from(SupportTicket ticket, List<SupportMessage> thread) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getReference(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                thread == null ? null : thread.stream().map(Message::from).toList());
    }
}
