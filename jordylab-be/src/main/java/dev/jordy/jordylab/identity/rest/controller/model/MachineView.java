package dev.jordy.jordylab.identity.rest.controller.model;

import dev.jordy.jordylab.identity.domain.MachineToken;

import java.time.Instant;
import java.util.UUID;

/** A machine in the admin's list — never its token value (spec 019 US5). */
public record MachineView(UUID id, String name, String scope, Instant createdAt, Instant lastUsedAt,
        Instant revokedAt) {

    public static MachineView from(MachineToken token) {
        return new MachineView(token.getId(), token.getName(), token.getScope().name(), token.getCreatedDate(),
                token.getLastUsedAt(), token.getRevokedAt());
    }
}
