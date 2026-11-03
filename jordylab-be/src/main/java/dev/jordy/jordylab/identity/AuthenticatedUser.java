package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.shared.security.UserIdAware;

import java.io.Serializable;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * The signed-in person as every other module sees them (spec 019 FR-009, D6) — the narrow public API of the identity
 * module. {@code id} is the same UUID the owner columns already hold (the Keycloak subject for migrated users).
 *
 * <p>{@link Serializable} is required: the session principal is stored in Spring Session JDBC and serialized with the
 * security context (spec 019 US3).
 */
public record AuthenticatedUser(UUID id, String username, String role, Set<ManagedApp> appGrants)
        implements UserIdAware, Serializable, Principal {

    /** The session's principal name: short, stable and equal to the username (Spring Session stores it, max 100). */
    @Override
    public String getName() {
        return username;
    }

    @Override
    public String userId() {
        return id.toString();
    }
}
