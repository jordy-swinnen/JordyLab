package dev.jordy.jordylab.identity.rest.controller.model;

import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One row of the admin's Users page (spec 019 US4, parity with spec 014). */
public record UserView(UUID id, String username, String displayName, String role, String status,
        Set<String> appGrants, Instant createdAt, PendingResetRequestView pendingResetRequest) {

    public static UserView from(UserAccount account) {
        return from(account, null);
    }

    public static UserView from(UserAccount account, PendingResetRequestView pendingResetRequest) {
        Set<String> grants = new LinkedHashSet<>();
        for (ManagedApp app : account.getAppGrants()) {
            grants.add(app.key());
        }

        return new UserView(account.getId(), account.getUsername(), account.getDisplayName(),
                account.roleKey().orElse(null), account.getStatus().name(), grants,
                account.getCreatedDate(), pendingResetRequest);
    }
}
