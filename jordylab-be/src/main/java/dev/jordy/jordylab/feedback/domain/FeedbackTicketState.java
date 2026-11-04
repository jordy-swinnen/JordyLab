package dev.jordy.jordylab.feedback.domain;

/**
 * A ticket's triage state (spec 017 FR-007): {@code NEW} → {@code ACCEPTED}, {@code BACKLOG} or
 * {@code REJECTED}; {@code BACKLOG} may still become {@code ACCEPTED} or {@code REJECTED};
 * {@code ACCEPTED} and {@code REJECTED} are terminal (Linear becomes the source of truth after an
 * accept, and a rejected ticket is archived for good).
 */
public enum FeedbackTicketState {

    NEW,
    ACCEPTED,
    BACKLOG,
    REJECTED
}
