package dev.jordy.jordylab.feedback.rest.controller.model;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;

import java.time.Instant;
import java.util.UUID;

/**
 * The admin view of a ticket — the guest view plus the sender, the private note, the Linear link
 * and the last accept error (contracts/rest-api.md). None of the additions ever reach a guest.
 */
public record InboxTicketView(
        UUID id,
        String app,
        String type,
        String title,
        String description,
        String state,
        String reply,
        String privateNote,
        String sender,
        Instant createdAt,
        String linearIssueUrl,
        String acceptError) {

    public static InboxTicketView from(FeedbackTicket ticket) {
        return new InboxTicketView(
                ticket.getId(),
                ticket.getApp().getDisplayName(),
                ticket.getType().getDisplayName(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getState().name(),
                ticket.getReply(),
                ticket.getPrivateNote(),
                ticket.getSenderDisplayName(),
                ticket.getCreatedDate(),
                ticket.getLinearIssueUrl(),
                ticket.getAcceptError());
    }
}
