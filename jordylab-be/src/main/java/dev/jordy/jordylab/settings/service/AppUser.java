package dev.jordy.jordylab.settings.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A view over a Keycloak user (data-model.md) — never persisted by JordyLab. */
public record AppUser(UUID id, String email, String firstName, String lastName, Instant createdAt, boolean enabled,
        Set<String> realmRoles, UserStatus status) {
}
