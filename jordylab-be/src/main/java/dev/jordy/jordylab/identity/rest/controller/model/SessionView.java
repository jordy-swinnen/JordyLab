package dev.jordy.jordylab.identity.rest.controller.model;

import java.time.Instant;

/** One web session of an account, as the admin's session panel shows it (spec 019 US4). */
public record SessionView(String sessionId, Instant startedAt, Instant lastAccessedAt) {
}
