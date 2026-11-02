package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.shared.event.UserAccessRemoved;
import dev.jordy.jordylab.shared.event.UserDeleted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OTHER_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final Instant CREATED_AT = Instant.parse("2026-10-20T09:12:00Z");
    private static final String GUEST_SUBJECT = GUEST_ID.toString();

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private GuestChatUsageRepository guestChatUsageRepository;

    @Mock
    private GuestChatLimitService guestChatLimitService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserDeletionService service;

    @BeforeEach
    void setUp() {
        service = new UserDeletionService(keycloakAdminClient, guestChatUsageRepository, guestChatLimitService,
                eventPublisher, TransactionOperations.withoutTransaction());
    }

    @Test
    void theAdminCannotDeleteThemselves() {
        assertThatThrownBy(() -> service.deletePerson(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(CannotChangeOwnAccountException.class);
        verify(keycloakAdminClient, never()).findUser(GUEST_SUBJECT);
    }

    @Test
    void anAdminAccountIsProtectedBeforeAnythingChanges() {
        when(keycloakAdminClient.findUser(OTHER_ADMIN_ID.toString()))
                .thenReturn(Optional.of(user(OTHER_ADMIN_ID, List.of("admin"))));

        assertThatThrownBy(() -> service.deletePerson(ADMIN_ID, OTHER_ADMIN_ID))
                .isInstanceOf(AdminAccountProtectedException.class);
        verify(keycloakAdminClient, never()).setEnabled(OTHER_ADMIN_ID.toString(), false);
    }

    @Test
    void deletesInTheSafeOrderAndAnnouncesIt() {
        when(keycloakAdminClient.findUser(GUEST_SUBJECT))
                .thenReturn(Optional.of(user(GUEST_ID, List.of("guest", "app-gamecatalog"))));

        service.deletePerson(ADMIN_ID, GUEST_ID);

        InOrder order = inOrder(keycloakAdminClient);
        order.verify(keycloakAdminClient).setEnabled(GUEST_SUBJECT, false);
        order.verify(keycloakAdminClient).endSessions(GUEST_SUBJECT);
        order.verify(keycloakAdminClient).revokeConsent(GUEST_SUBJECT, "jordylab-mobile");
        order.verify(keycloakAdminClient).deleteUser(GUEST_SUBJECT);
        verify(guestChatUsageRepository).deleteByUserSubject(GUEST_SUBJECT);
        verify(guestChatLimitService).clearPersonal(GUEST_SUBJECT);
        verify(eventPublisher).publishEvent(new UserAccessRemoved(GUEST_SUBJECT));
        verify(eventPublisher).publishEvent(new UserDeleted(GUEST_SUBJECT));
    }

    @Test
    void aMissingMobileConsentIsNotAFailure() {
        when(keycloakAdminClient.findUser(GUEST_SUBJECT)).thenReturn(Optional.of(user(GUEST_ID, List.of("guest"))));
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null))
                .when(keycloakAdminClient).revokeConsent(GUEST_SUBJECT, "jordylab-mobile");

        service.deletePerson(ADMIN_ID, GUEST_ID);

        verify(keycloakAdminClient).deleteUser(GUEST_SUBJECT);
    }

    /** FR-017: the account is blocked first, so a later failure leaves nothing usable, and a retry can finish. */
    @Test
    void aFailureAtTheLastStageReportsItAndLeavesThePersonDisabled() {
        when(keycloakAdminClient.findUser(GUEST_SUBJECT)).thenReturn(Optional.of(user(GUEST_ID, List.of("guest"))));
        doThrow(new IllegalStateException("Keycloak exploded")).when(keycloakAdminClient).deleteUser(GUEST_SUBJECT);

        assertThatThrownBy(() -> service.deletePerson(ADMIN_ID, GUEST_ID))
                .isInstanceOfSatisfying(UserDeletionFailedException.class, exception ->
                        assertSoftly(softly -> softly.assertThat(exception.stage())
                                .isEqualTo(UserDeletionFailedException.Stage.DELETE_ACCOUNT)));

        verify(keycloakAdminClient).setEnabled(GUEST_SUBJECT, false);
        verify(guestChatUsageRepository).deleteByUserSubject(GUEST_SUBJECT);
    }

    @Test
    void anUnknownPersonIsNotFound() {
        when(keycloakAdminClient.findUser(GUEST_SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePerson(ADMIN_ID, GUEST_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    private KeycloakAdminClient.KeycloakUser user(UUID id, List<String> roles) {
        return new KeycloakAdminClient.KeycloakUser(id.toString(), "friend@example.org", "Ada", "Palmer", CREATED_AT,
                true, roles);
    }
}
