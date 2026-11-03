package dev.jordy.jordylab.feedback.rest.controller.model;

/**
 * The accept request's optional body: the note is the ticket's private note (stored and included
 * in the Linear issue when set). Absent body means "no note".
 */
public record AcceptRequest(String privateNote) {
}
