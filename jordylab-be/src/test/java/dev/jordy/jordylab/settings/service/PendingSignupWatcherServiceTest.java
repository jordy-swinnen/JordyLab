package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.UserSignUpPending;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingSignupWatcherServiceTest {

    private static final UUID FRIEND_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant CREATED_AT = Instant.parse("2026-09-28T10:00:00Z");

    @Mock
    private KeycloakUserAdministrationService userAdministrationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PendingSignupWatcherService watcher;

    @BeforeEach
    void setUp() {
        watcher = new PendingSignupWatcherService(userAdministrationService, eventPublisher);
    }

    @Test
    void publishesOneEventForANewlySeenPendingUser() {
        AppUser friend = pendingUser(FRIEND_ID, "friend@example.org", "Ada", "Palmer");
        when(userAdministrationService.listUsers(UserStatus.PENDING)).thenReturn(List.of(friend));
        ArgumentCaptor<UserSignUpPending> eventCaptor = ArgumentCaptor.forClass(UserSignUpPending.class);

        watcher.checkForNewPendingSignups();

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserSignUpPending event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(FRIEND_ID);
        assertThat(event.email()).isEqualTo("friend@example.org");
        assertThat(event.displayName()).isEqualTo("Ada Palmer");
    }

    @Test
    void doesNotRepublishForAStillPendingUserOnTheNextPoll() {
        AppUser friend = pendingUser(FRIEND_ID, "friend@example.org", "Ada", "Palmer");
        when(userAdministrationService.listUsers(UserStatus.PENDING)).thenReturn(List.of(friend));
        ArgumentCaptor<UserSignUpPending> eventCaptor = ArgumentCaptor.forClass(UserSignUpPending.class);

        watcher.checkForNewPendingSignups();
        watcher.checkForNewPendingSignups();

        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).hasSize(1);
    }

    @Test
    void publishesNothingWhenNoOneIsPending() {
        when(userAdministrationService.listUsers(UserStatus.PENDING)).thenReturn(List.of());

        watcher.checkForNewPendingSignups();

        verifyNoInteractions(eventPublisher);
    }

    private AppUser pendingUser(UUID id, String email, String firstName, String lastName) {
        return new AppUser(id, email, firstName, lastName, CREATED_AT, true, Set.of(), UserStatus.PENDING);
    }
}
