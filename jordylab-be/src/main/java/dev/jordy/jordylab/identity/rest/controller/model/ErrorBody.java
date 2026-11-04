package dev.jordy.jordylab.identity.rest.controller.model;

/** The identity module's error shape: a stable reason code plus a human-readable message. */
public record ErrorBody(String reason, String message) {
}
