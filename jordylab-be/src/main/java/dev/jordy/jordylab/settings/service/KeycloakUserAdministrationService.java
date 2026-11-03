package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.settings.util.TemporaryPasswordGenerator;
import dev.jordy.jordylab.identity.UserAccessRemoved;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

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
    private final GuestChatLimitService guestChatLimitService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactions;

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

    /**
     * Approves a person and gives them the default grants (spec 014 FR-008). A person approved again after a revoke
     * starts from the same default set: earlier grants are never restored.
     */
    public void approve(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        if (!user.enabled()) {
            keycloakAdminClient.setEnabled(user.id(), true);
        }
        if (!user.realmRoles().contains(GUEST_ROLE)) {
            keycloakAdminClient.grantRealmRole(user.id(), GUEST_ROLE);
        }
        for (ManagedApp app : ManagedApp.defaultGrants()) {
            String roleName = app.grantRole().orElseThrow(() -> new AppNotGrantableException(app.key()));
            if (!user.realmRoles().contains(roleName)) {
                keycloakAdminClient.grantRealmRole(user.id(), roleName);
            }
        }
    }

    public void reject(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        guardLastAdmin(user);
        keycloakAdminClient.setEnabled(user.id(), false);
        publishAccessRemoved(user.id());
    }

    public void revoke(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        if (!user.realmRoles().contains(GUEST_ROLE)) {
            throw new UserNotApprovedException(userId);
        }
        guardLastAdmin(user);
        revokeEveryAppRole(user);
        keycloakAdminClient.revokeRealmRole(user.id(), GUEST_ROLE);
        keycloakAdminClient.endSessions(user.id());
        // Spec 014 FR-010: a revoke clears the personal limit, so a later approval starts from the default.
        guestChatLimitService.clearPersonal(user.id());
        publishAccessRemoved(user.id());
    }

    /** Returned once, to the admin, for out-of-band sharing — never logged or stored. */
    public String resetPassword(UUID userId) {
        KeycloakAdminClient.KeycloakUser user = requireUser(userId);
        String temporaryPassword = TemporaryPasswordGenerator.generate();
        keycloakAdminClient.resetPassword(user.id(), temporaryPassword);

        return temporaryPassword;
    }

    /** A revoke removes every app grant too, so a later approval starts again from the default set (FR-008). */
    private void revokeEveryAppRole(KeycloakAdminClient.KeycloakUser user) {
        for (ManagedApp app : ManagedApp.values()) {
            app.grantRole()
                    .filter(user.realmRoles()::contains)
                    .ifPresent(roleName -> keycloakAdminClient.revokeRealmRole(user.id(), roleName));
        }
    }

    /** Inside a transaction, because module listeners only run once the publishing transaction commits. */
    private void publishAccessRemoved(String userSubject) {
        transactions.executeWithoutResult(status -> eventPublisher.publishEvent(new UserAccessRemoved(userSubject)));
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

        // Direct role mappings only: an admin holds `admin` (a composite that includes guest, spec 011 Q-07)
        // but not `guest` itself, and must never be listed as a pending sign-up (BUG-034).
        boolean approved = user.realmRoles().contains(GUEST_ROLE) || user.realmRoles().contains(ADMIN_ROLE);

        return approved ? UserStatus.APPROVED : UserStatus.PENDING;
    }
}
