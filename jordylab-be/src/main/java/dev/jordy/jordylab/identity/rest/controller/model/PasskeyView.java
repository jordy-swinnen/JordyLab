package dev.jordy.jordylab.identity.rest.controller.model;

import java.time.Instant;

/** One enrolled passkey in the account's Settings card (spec 019 US2). */
public record PasskeyView(String credentialId, String label, Instant createdAt, Instant lastUsedAt) {
}
