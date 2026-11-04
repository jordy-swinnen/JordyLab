package dev.jordy.jordylab.feedback.rest.controller.model;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;

import java.time.Instant;
import java.util.UUID;

/**
 * The guest view of a ticket — the whole surface a guest may ever see (spec 017 FR-013/014/018).
 * Deliberately absent: the sender, the private note, the Linear link and any Linear-side state.
 */
public record GuestTicketView(
        UUID id,
        String app,
        String type,
        String title,
        String description,
        String state,
        String reply,
        Instant createdAt) {

    public static GuestTicketView from(FeedbackTicket ticket) {
        return new GuestTicketView(
                ticket.getId(),
                ticket.getApp().getDisplayName(),
                ticket.getType().getDisplayName(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getState().name(),
                ticket.getReply(),
                ticket.getCreatedDate());
    }
}
