package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The admin's per-person chat limit (spec 014 FR-010, FR-013): refused for their own account, and refused for an admin,
 * who is never limited. Everything is checked before the limit table is touched.
 */
@Service
@RequiredArgsConstructor
public class UserChatLimitService {

    private static final String ADMIN_ROLE = "admin";

    private final KeycloakAdminClient keycloakAdminClient;
    private final GuestChatLimitService guestChatLimitService;

    public void setPersonal(UUID callerId, UUID targetUserId, int dailyLimit) {
        KeycloakAdminClient.KeycloakUser target = requireTarget(callerId, targetUserId);
        if (target.realmRoles().contains(ADMIN_ROLE)) {
            throw new AdminAccountProtectedException(targetUserId);
        }
        guestChatLimitService.savePersonal(target.id(), dailyLimit, callerId.toString());
    }

    /** Idempotent: clearing a limit that was never set is accepted. */
    public void clearPersonal(UUID callerId, UUID targetUserId) {
        requireTarget(callerId, targetUserId);
        guestChatLimitService.clearPersonal(targetUserId.toString());
    }

    private KeycloakAdminClient.KeycloakUser requireTarget(UUID callerId, UUID targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new CannotChangeOwnAccountException(targetUserId);
        }

        return keycloakAdminClient.findUser(targetUserId.toString())
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
    }
}
