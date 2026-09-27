package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.settings.util.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates user administration over {@link KeycloakAdminClient} (spec FR-006/FR-007,
 * research D6): status is always derived from Keycloak's own {@code enabled}/{@code guest}
 * state, never stored, and every mutation checks the last-admin invariant before touching
 * anything.
 */
@Service
@RequiredArgsConstructor
public class KeycloakUserAdministrationService {

    private static final String ADMIN_ROLE = "admin";
    private static final String GUEST_ROLE = "guest";

    private final KeycloakAdminClient keycloakAdminClient;

    public List<AppUser> listUsers(UserStatus statusFilter) {
        return keycloakAdminClient.listUsers().stream()
                .map(this::toAppUser)
                .filter(user -> statusFilter == null || user.status() == statusFilter)
                .sorted(Comparator.comparing(AppUser::createdAt))
                .toList();
    }

    public long pendingCount() {
        return listUsers(UserStatus.PENDING).size();
    }

    public void approve(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        if (!user.enabled()) {
            keycloakAdminClient.setEnabled(user.id(), true);
        }
        if (!user.realmRoles().contains(GUEST_ROLE)) {
            keycloakAdminClient.grantRealmRole(user.id(), GUEST_ROLE);
        }
    }

    public void reject(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        guardLastAdmin(user);
        keycloakAdminClient.setEnabled(user.id(), false);
    }

    public void revoke(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        if (!user.realmRoles().contains(GUEST_ROLE)) {
            throw new UserNotApprovedException(userId);
        }
        guardLastAdmin(user);
        keycloakAdminClient.revokeRealmRole(user.id(), GUEST_ROLE);
        keycloakAdminClient.endSessions(user.id());
    }

    /** Returned once, to the admin, for out-of-band sharing — never logged or stored. */
    public String resetPassword(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        String temporaryPassword = TemporaryPasswordGenerator.generate();
        keycloakAdminClient.resetPassword(user.id(), temporaryPassword);

        return temporaryPassword;
    }

    private void guardLastAdmin(KeycloakAdminClient.KeycloakUser target) {
        if (!target.enabled() || !target.realmRoles().contains(ADMIN_ROLE)) {
            return;
        }
        long enabledAdmins = keycloakAdminClient.listUsers().stream()
                .filter(KeycloakAdminClient.KeycloakUser::enabled)
                .filter(candidate -> candidate.realmRoles().contains(ADMIN_ROLE))
                .count();
        if (enabledAdmins <= 1) {
            throw new LastAdminProtectedException();
        }
    }

    private KeycloakAdminClient.KeycloakUser requireUser(UUID userId) {
        return keycloakAdminClient.findUser(userId.toString())
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private AppUser toAppUser(KeycloakAdminClient.KeycloakUser user) {
        return new AppUser(
                UUID.fromString(user.id()),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.createdAt(),
                user.enabled(),
                Set.copyOf(user.realmRoles()),
                deriveStatus(user));
    }

    private UserStatus deriveStatus(KeycloakAdminClient.KeycloakUser user) {
        if (!user.enabled()) {
            return UserStatus.REJECTED;
        }

        return user.realmRoles().contains(GUEST_ROLE) ? UserStatus.APPROVED : UserStatus.PENDING;
    }
}
