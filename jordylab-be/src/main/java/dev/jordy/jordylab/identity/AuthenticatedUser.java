package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.shared.security.UserIdAware;

import java.util.Set;
import java.util.UUID;

/**
 * The signed-in person as every other module sees them (spec 019 FR-009, D6) — the narrow public API of the identity
 * module. {@code id} is the same UUID the owner columns already hold (the Keycloak subject for migrated users).
 */
public record AuthenticatedUser(UUID id, String username, String role, Set<ManagedApp> appGrants)
        implements UserIdAware {

    @Override
    public String userId() {
        return id.toString();
    }
}
