package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.shared.rest.client.NtfyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * One admin push per newly-seen pending sign-up, with the Users-screen click-through (spec 017 US3,
 * contracts/notifications-contract.md) — a duplicate-push regression here would mean two notifications for one sign-up.
 */
@ExtendWith(MockitoExtension.class)
class PendingSignupWatcherServiceTest {

    private static final UUID FRIEND_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant CREATED_AT = Instant.parse("2026-09-28T10:00:00Z");
    private static final String TITLE = "New JordyLab sign-up";
    private static final String USERS_PATH = "/settings/users";

    @Mock
    private KeycloakUserAdministrationService userAdministrationService;

    @Mock
    private NtfyClient ntfyClient;

    private PendingSignupWatcherService watcher;

    @BeforeEach
    void setUp() {
        watcher = new PendingSignupWatcherService(userAdministrationService, ntfyClient);
    }

    @Test
    void pushesOneNotificationForANewlySeenPendingUser() {
        AppUser friend = pendingUser(FRIEND_ID, "friend@example.org", "Ada", "Palmer");
        when(userAdministrationService.listUsers(UserStatus.PENDING)).thenReturn(List.of(friend));
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        watcher.checkForNewPendingSignups();

        verify(ntfyClient).publish(eq(TITLE), bodyCaptor.capture(), eq(USERS_PATH));
        assertThat(bodyCaptor.getValue()).isEqualTo("Ada Palmer (friend@example.org) is waiting for approval.");
    }

    @Test
    void doesNotPushAgainForAStillPendingUserOnTheNextPoll() {
        AppUser friend = pendingUser(FRIEND_ID, "friend@example.org", "Ada", "Palmer");
        when(userAdministrationService.listUsers(UserStatus.PENDING)).thenReturn(List.of(friend));

        watcher.checkForNewPendingSignups();
        watcher.checkForNewPendingSignups();

        verify(ntfyClient, times(1)).publish(eq(TITLE), contains("Ada Palmer"), eq(USERS_PATH));
    }

    @Test
    void pushesNothingWhenNoOneIsPending() {
        when(userAdministrationService.listUsers(UserStatus.PENDING)).thenReturn(List.of());

        watcher.checkForNewPendingSignups();

        verifyNoInteractions(ntfyClient);
    }

    private AppUser pendingUser(UUID id, String email, String firstName, String lastName) {
        return new AppUser(id, email, firstName, lastName, CREATED_AT, true, Set.of(), UserStatus.PENDING);
    }
}
