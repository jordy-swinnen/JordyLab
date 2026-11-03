package dev.jordy.jordylab.identity.rest.controller.model;

/** A public sign-up: username and password only — no email is asked for (spec 019 FR-001). */
public record RegisterRequest(String username, String password, String displayName) {
}
