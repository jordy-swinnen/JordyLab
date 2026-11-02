package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.shared.event.UserAccessRemoved;
import dev.jordy.jordylab.shared.event.UserDeleted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

/**
 * Deletes a person completely (spec 014 FR-014 to FR-017, research R4). The order is deliberate and every step is safe
 * to repeat:
 *
 * <ol>
 *   <li>refuse the admin's own account and any admin account, before anything changes;</li>
 *   <li>disable the account, so signing in and token refresh stop working immediately;</li>
 *   <li>end every session, including the phone app's offline sessions (its consent is revoked);</li>
 *   <li>remove the local data in one transaction and announce the deletion to other modules;</li>
 *   <li>delete the account last, so a failure there leaves a blocked account the admin can retry.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private static final String ADMIN_ROLE = "admin";
    private static final String MOBILE_CLIENT_ID = "jordylab-mobile";

    private final KeycloakAdminClient keycloakAdminClient;
    private final GuestChatUsageRepository guestChatUsageRepository;
    private final GuestChatLimitService guestChatLimitService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactions;

    public void deletePerson(UUID callerId, UUID targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new CannotChangeOwnAccountException(targetUserId);
        }
        KeycloakAdminClient.KeycloakUser target = keycloakAdminClient.findUser(targetUserId.toString())
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
        if (target.realmRoles().contains(ADMIN_ROLE)) {
            throw new AdminAccountProtectedException(targetUserId);
        }

        String subject = target.id();
        runStage(UserDeletionFailedException.Stage.DISABLE, () -> keycloakAdminClient.setEnabled(subject, false));
        runStage(UserDeletionFailedException.Stage.END_SESSIONS, () -> endEverySession(subject));
        runStage(UserDeletionFailedException.Stage.LOCAL_CLEANUP, () -> removeLocalData(subject));
        runStage(UserDeletionFailedException.Stage.DELETE_ACCOUNT, () -> keycloakAdminClient.deleteUser(subject));
    }

    private void endEverySession(String subject) {
        keycloakAdminClient.endSessions(subject);
        try {
            keycloakAdminClient.revokeConsent(subject, MOBILE_CLIENT_ID);
        } catch (HttpClientErrorException.NotFound neverUsedThePhoneApp) {
            // Expected for most people: a plain logout does not end offline_access sessions, so revoke them when present.
        }
    }

    private void removeLocalData(String subject) {
        transactions.executeWithoutResult(status -> {
            guestChatUsageRepository.deleteByUserSubject(subject);
            guestChatLimitService.clearPersonal(subject);
            eventPublisher.publishEvent(new UserAccessRemoved(subject));
            eventPublisher.publishEvent(new UserDeleted(subject));
        });
    }

    private void runStage(UserDeletionFailedException.Stage stage, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            throw new UserDeletionFailedException(stage, failure);
        }
    }
}
