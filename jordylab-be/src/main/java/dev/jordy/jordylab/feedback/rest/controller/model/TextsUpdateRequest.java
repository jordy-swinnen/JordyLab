package dev.jordy.jordylab.feedback.rest.controller.model;

/**
 * A wholesale replacement of both admin texts; an empty string clears its text (FR-008).
 */
public record TextsUpdateRequest(String reply, String privateNote) {
}
