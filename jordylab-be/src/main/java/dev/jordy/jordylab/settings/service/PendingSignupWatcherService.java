package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.shared.rest.client.NtfyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Polls for newly-pending sign-ups and pushes one admin notification per new arrival (spec 017 US3,
 * contracts/notifications-contract.md) through the shared {@link NtfyClient}, with a click-through to the Users
 * screen. Superseded design: this used to publish a {@code UserSignUpPending} event so the {@code mobile} module could
 * listen — that module is gone, so the sender lives here. The "last seen" set is in-memory, matching 006's decision
 * (D6) that user status is derived from Keycloak, never persisted — losing this set on restart just means the next poll
 * treats every still-pending user as newly seen once, an acceptable cost since a restart is rare and a repeat
 * notification is harmless.
 */
@Component
@RequiredArgsConstructor
public class PendingSignupWatcherService {

    private static final long POLL_DELAY_MS = 5 * 60 * 1000;
    private static final String NOTIFICATION_TITLE = "New JordyLab sign-up";
    private static final String USERS_SCREEN_PATH = "/settings/users";

    private final KeycloakUserAdministrationService userAdministrationService;
    private final NtfyClient ntfyClient;

    private final Set<UUID> lastSeenPendingUserIds = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = POLL_DELAY_MS)
    @Transactional
    public void checkForNewPendingSignups() {
        List<AppUser> pending = userAdministrationService.listUsers(UserStatus.PENDING);

        for (AppUser user : pending) {
            if (lastSeenPendingUserIds.add(user.id())) {
                String displayName = (user.firstName() + " " + user.lastName()).trim();
                ntfyClient.publish(NOTIFICATION_TITLE,
                        displayName + " (" + user.email() + ") is waiting for approval.",
                        USERS_SCREEN_PATH);
            }
        }

        Set<UUID> currentlyPendingIds = pending.stream().map(AppUser::id).collect(Collectors.toSet());
        lastSeenPendingUserIds.retainAll(currentlyPendingIds);
    }
}
