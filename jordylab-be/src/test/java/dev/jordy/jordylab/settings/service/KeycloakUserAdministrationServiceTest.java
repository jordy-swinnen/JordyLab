package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserAdministrationServiceTest {

    private static final UUID PENDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SOLE_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SECOND_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant CREATED_AT = Instant.parse("2026-09-27T10:00:00Z");

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    private KeycloakUserAdministrationService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakUserAdministrationService(keycloakAdminClient);
    }

    @Test
    void listsUsersWithDerivedStatusOrderedByCreatedAt() {
        KeycloakAdminClient.KeycloakUser pending = new KeycloakAdminClient.KeycloakUser(PENDING_ID.toString(),
                "pending@example.org", "First", "Last", CREATED_AT, true, List.of());
        KeycloakAdminClient.KeycloakUser guest = new KeycloakAdminClient.KeycloakUser(GUEST_ID.toString(),
                "guest@example.org", "First", "Last", CREATED_AT.plusSeconds(60), true, List.of("guest"));
        when(keycloakAdminClient.listUsers()).thenReturn(List.of(guest, pending));

        List<AppUser> users = service.listUsers(null);

        assertSoftly(softly -> {
            softly.assertThat(users).hasSize(2);
            softly.assertThat(users.get(0).id()).isEqualTo(PENDING_ID);
            softly.assertThat(users.get(0).status()).isEqualTo(UserStatus.PENDING);
            softly.assertThat(users.get(1).id()).isEqualTo(GUEST_ID);
            softly.assertThat(users.get(1).status()).isEqualTo(UserStatus.APPROVED);
        });
    }

    @Test
    void filtersUsersByDerivedStatus() {
        KeycloakAdminClient.KeycloakUser pending = keycloakUser(PENDING_ID, true, List.of());
        KeycloakAdminClient.KeycloakUser guest = keycloakUser(GUEST_ID, true, List.of("guest"));
        when(keycloakAdminClient.listUsers()).thenReturn(List.of(pending, guest));

        List<AppUser> users = service.listUsers(UserStatus.APPROVED);

        assertThat(users).extracting(AppUser::id).containsExactly(GUEST_ID);
    }

    @Test
    void pendingCountCountsOnlyPendingUsers() {
        KeycloakAdminClient.KeycloakUser pending = keycloakUser(PENDING_ID, true, List.of());
        KeycloakAdminClient.KeycloakUser guest = keycloakUser(GUEST_ID, true, List.of("guest"));
        when(keycloakAdminClient.listUsers()).thenReturn(List.of(pending, guest));

        assertThat(service.pendingCount()).isEqualTo(1L);
    }

    @Test
    void approvingAPendingUserGrantsGuestWithoutTouchingEnabled() {
        when(keycloakAdminClient.findUser(PENDING_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(PENDING_ID, true, List.of())));

        service.approve(PENDING_ID);

        verify(keycloakAdminClient).grantRealmRole(PENDING_ID.toString(), "guest");
        verify(keycloakAdminClient, never()).setEnabled(PENDING_ID.toString(), true);
    }

    @Test
    void approvingARejectedUserReEnablesAndGrantsGuest() {
        when(keycloakAdminClient.findUser(PENDING_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(PENDING_ID, false, List.of())));

        service.approve(PENDING_ID);

        verify(keycloakAdminClient).setEnabled(PENDING_ID.toString(), true);
        verify(keycloakAdminClient).grantRealmRole(PENDING_ID.toString(), "guest");
    }

    @Test
    void approvingAnAlreadyApprovedUserIsANoOp() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(GUEST_ID, true, List.of("guest"))));

        service.approve(GUEST_ID);

        verify(keycloakAdminClient, never()).setEnabled(GUEST_ID.toString(), true);
        verify(keycloakAdminClient, never()).grantRealmRole(GUEST_ID.toString(), "guest");
    }

    @Test
    void rejectingAGuestDisablesTheAccount() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(GUEST_ID, true, List.of("guest"))));

        service.reject(GUEST_ID);

        verify(keycloakAdminClient).setEnabled(GUEST_ID.toString(), false);
    }

    @Test
    void rejectingTheSoleEnabledAdminIsBlocked() {
        KeycloakAdminClient.KeycloakUser soleAdmin = keycloakUser(SOLE_ADMIN_ID, true, List.of("admin"));
        when(keycloakAdminClient.findUser(SOLE_ADMIN_ID.toString())).thenReturn(Optional.of(soleAdmin));
        when(keycloakAdminClient.listUsers()).thenReturn(List.of(soleAdmin));

        assertThatThrownBy(() -> service.reject(SOLE_ADMIN_ID)).isInstanceOf(LastAdminProtectedException.class);
        verify(keycloakAdminClient, never()).setEnabled(SOLE_ADMIN_ID.toString(), false);
    }

    @Test
    void rejectingOneOfTwoAdminsIsAllowed() {
        KeycloakAdminClient.KeycloakUser firstAdmin = keycloakUser(SOLE_ADMIN_ID, true, List.of("admin"));
        KeycloakAdminClient.KeycloakUser secondAdmin = keycloakUser(SECOND_ADMIN_ID, true, List.of("admin"));
        when(keycloakAdminClient.findUser(SOLE_ADMIN_ID.toString())).thenReturn(Optional.of(firstAdmin));
        when(keycloakAdminClient.listUsers()).thenReturn(List.of(firstAdmin, secondAdmin));

        service.reject(SOLE_ADMIN_ID);

        verify(keycloakAdminClient).setEnabled(SOLE_ADMIN_ID.toString(), false);
    }

    @Test
    void revokingAGuestRemovesTheRoleEndsSessionsAndRevokesMobileOfflineConsent() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(GUEST_ID, true, List.of("guest"))));

        service.revoke(GUEST_ID);

        verify(keycloakAdminClient).revokeRealmRole(GUEST_ID.toString(), "guest");
        verify(keycloakAdminClient).endSessions(GUEST_ID.toString());
        // spec 007 FR-013 / research D12: a plain session logout does not revoke offline_access
        // grants, so revoke() must separately kill the mobile app's biometric-unlock consent.
        verify(keycloakAdminClient).revokeConsent(GUEST_ID.toString(), "jordylab-mobile");
    }

    @Test
    void revokingAGuestWhoNeverUsedTheMobileAppIsNotAnError() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(GUEST_ID, true, List.of("guest"))));
        HttpClientErrorException noSuchConsent = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        doThrow(noSuchConsent).when(keycloakAdminClient).revokeConsent(GUEST_ID.toString(), "jordylab-mobile");

        service.revoke(GUEST_ID);

        verify(keycloakAdminClient).revokeRealmRole(GUEST_ID.toString(), "guest");
        verify(keycloakAdminClient).endSessions(GUEST_ID.toString());
    }

    @Test
    void revokingAUserWithoutGuestIsRejected() {
        when(keycloakAdminClient.findUser(PENDING_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(PENDING_ID, true, List.of())));

        assertThatThrownBy(() -> service.revoke(PENDING_ID)).isInstanceOf(UserNotApprovedException.class);
        verify(keycloakAdminClient, never()).revokeRealmRole(PENDING_ID.toString(), "guest");
    }

    @Test
    void resetPasswordGeneratesAndSendsATemporaryPassword() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(keycloakUser(GUEST_ID, true, List.of("guest"))));
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);

        String returnedPassword = service.resetPassword(GUEST_ID);

        verify(keycloakAdminClient).resetPassword(eq(GUEST_ID.toString()), passwordCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(passwordCaptor.getValue()).isEqualTo(returnedPassword);
            softly.assertThat(returnedPassword).hasSize(20);
        });
    }

    @Test
    void mutatingAnUnknownUserRaisesUserNotFound() {
        when(keycloakAdminClient.findUser(PENDING_ID.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(PENDING_ID)).isInstanceOf(UserNotFoundException.class);
    }

    private KeycloakAdminClient.KeycloakUser keycloakUser(UUID id, boolean enabled, List<String> roles) {
        return new KeycloakAdminClient.KeycloakUser(id.toString(), "user-" + id + "@example.org", "First", "Last",
                CREATED_AT, enabled, roles);
    }
}
