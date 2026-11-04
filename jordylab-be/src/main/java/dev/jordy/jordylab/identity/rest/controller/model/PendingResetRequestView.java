package dev.jordy.jordylab.identity.rest.controller.model;

import java.time.Instant;
import java.util.UUID;

/** A locked-out user's pending reset request, shown on the admin's user card (spec 019 US6). */
public record PendingResetRequestView(UUID requestId, Instant requestedAt) {
}
