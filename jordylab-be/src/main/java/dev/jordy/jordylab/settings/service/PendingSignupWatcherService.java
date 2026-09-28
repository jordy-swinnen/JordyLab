package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.UserSignUpPending;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Polls for newly-pending sign-ups and publishes one {@link UserSignUpPending} event per new
 * arrival (spec 007 FR-016, research D9) — this supersedes 006's original plan of calling Ntfy
 * directly from here, since that was never built (only the config scaffold existed) and
 * {@code mobile} is now the sole Ntfy sender, adding the App-Link click-through the plain
 * sign-up push never had. The "last seen" set is in-memory, matching 006's decision (D6) that
 * user status is derived from Keycloak, never persisted — losing this set on restart just means
 * the next poll treats every still-pending user as newly seen once, an acceptable cost since a
 * restart is rare and a repeat notification is harmless.
 */
@Component
@RequiredArgsConstructor
public class PendingSignupWatcherService {

    private static final long POLL_DELAY_MS = 5 * 60 * 1000;

    private final KeycloakUserAdministrationService userAdministrationService;
    private final ApplicationEventPublisher eventPublisher;

    private final Set<UUID> lastSeenPendingUserIds = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = POLL_DELAY_MS)
    public void checkForNewPendingSignups() {
        List<AppUser> pending = userAdministrationService.listUsers(UserStatus.PENDING);

        for (AppUser user : pending) {
            if (lastSeenPendingUserIds.add(user.id())) {
                eventPublisher.publishEvent(new UserSignUpPending(user.id(), user.email(),
                        (user.firstName() + " " + user.lastName()).trim()));
            }
        }

        Set<UUID> currentlyPendingIds = pending.stream().map(AppUser::id).collect(Collectors.toSet());
        lastSeenPendingUserIds.retainAll(currentlyPendingIds);
    }
}
