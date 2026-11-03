package dev.jordy.jordylab.identity.rest.controller.model;

/** Optional role on approval; {@code guest} when absent (spec 019 FR-003). */
public record ApproveRequest(String role) {
}
