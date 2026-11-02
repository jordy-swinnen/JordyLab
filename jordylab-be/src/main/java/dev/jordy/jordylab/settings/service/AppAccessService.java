package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.shared.event.AppAccessRemoved;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.UUID;

/**
 * Grants and removes one app per person (spec 014 FR-001 to FR-008). A grant is a Keycloak realm role (research R1), so
 * the person receives it on their next token refresh; removing it takes the same route, and the person keeps their other
 * apps and their session (clarification 2026-11-02).
 *
 * <p>Everything is validated before the identity provider is touched: an unknown app, an app that can never be granted,
 * an unapproved person, the admin's own account. The last one matters because a grant to the admin would be meaningless
 * and a removal would lock the owner out of their own app.
 */
@Service
@RequiredArgsConstructor
public class AppAccessService {

    private static final String ADMIN_ROLE = "admin";
    private static final String GUEST_ROLE = "guest";

    private final KeycloakAdminClient keycloakAdminClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactions;

    public List<ManagedApp> apps() {
        return List.of(ManagedApp.values());
    }

    public void grantApp(UUID callerId, UUID targetUserId, String appKey) {
        ManagedApp app = requireGrantableApp(appKey);
        refuseOwnAccount(callerId, targetUserId);
        KeycloakAdminClient.KeycloakUser target = requireApprovedUser(targetUserId);
        String roleName = app.grantRole().orElseThrow(() -> new AppNotGrantableException(appKey));
        if (!target.realmRoles().contains(roleName)) {
            keycloakAdminClient.grantRealmRole(target.id(), roleName);
        }
    }

    /**
     * Removes one app from a person. A non-grantable app has no role to remove, so the request is accepted as a no-op
     * (it can never have been granted). Removing the Game Catalog also removes the person's votes in it (FR-002).
     */
    public void removeApp(UUID callerId, UUID targetUserId, String appKey) {
        ManagedApp app = requireApp(appKey);
        refuseOwnAccount(callerId, targetUserId);
        requireUser(targetUserId);
        if (!app.grantable()) {
            return;
        }
        String roleName = app.grantRole().orElseThrow(() -> new AppNotGrantableException(appKey));
        keycloakAdminClient.revokeRealmRole(targetUserId.toString(), roleName);
        publishAppAccessRemoved(targetUserId, app);
    }

    private ManagedApp requireApp(String appKey) {
        return ManagedApp.fromKey(appKey)
                .orElseThrow(() -> new AppNotFoundException(appKey));
    }

    private ManagedApp requireGrantableApp(String appKey) {
        ManagedApp app = requireApp(appKey);
        if (!app.grantable()) {
            throw new AppNotGrantableException(appKey);
        }

        return app;
    }

    private void refuseOwnAccount(UUID callerId, UUID targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new CannotChangeOwnAccountException(targetUserId);
        }
    }

    private KeycloakAdminClient.KeycloakUser requireApprovedUser(UUID targetUserId) {
        KeycloakAdminClient.KeycloakUser target = requireUser(targetUserId);
        boolean approved = target.enabled()
                && (target.realmRoles().contains(GUEST_ROLE) || target.realmRoles().contains(ADMIN_ROLE));
        if (!approved) {
            throw new UserNotApprovedException(targetUserId);
        }

        return target;
    }

    private KeycloakAdminClient.KeycloakUser requireUser(UUID targetUserId) {
        return keycloakAdminClient.findUser(targetUserId.toString())
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
    }

    /** Inside a transaction, because module listeners only run once the publishing transaction commits. */
    private void publishAppAccessRemoved(UUID userId, ManagedApp app) {
        transactions.executeWithoutResult(status -> eventPublisher.publishEvent(
                new AppAccessRemoved(userId.toString(), app.key())));
    }
}
