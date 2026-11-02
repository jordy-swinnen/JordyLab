package dev.jordy.jordylab.feedback;

/**
 * Published when a guest submits a ticket (spec 017 FR-017). Consumed by {@code mobile}'s
 * notification listener, which turns it into one Ntfy push — the record therefore carries only
 * metadata (who, which app, which type), never the ticket's title, description or any other text.
 */
public record FeedbackTicketCreated(String senderName, String appName, String typeName) {
}
