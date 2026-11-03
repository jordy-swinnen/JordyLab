package dev.jordy.jordylab.identity.rest.controller.model;

import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The signed-in user's own view (spec 019 contracts/identity-api.md): exactly what the frontend needs for the guards,
 * {@code isAdmin}, {@code hasGameCatalogAccess}, and the pending-approval screen.
 */
public record CurrentUserResponse(UUID id, String username, String displayName, String role, String status,
        Set<String> appGrants) {

    public static CurrentUserResponse from(UserAccount account) {
        Set<String> grants = new LinkedHashSet<>();
        for (ManagedApp app : account.getAppGrants()) {
            grants.add(app.key());
        }

        return new CurrentUserResponse(account.getId(), account.getUsername(), account.getDisplayName(),
                account.roleKey().orElse(null), account.getStatus().name(), grants);
    }
}
