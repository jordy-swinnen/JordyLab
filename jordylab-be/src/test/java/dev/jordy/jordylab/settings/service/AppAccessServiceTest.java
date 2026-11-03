package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.gamecatalog.GameCatalogAccess;
import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.identity.AppAccessRemoved;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppAccessServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PENDING_ID = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final Instant CREATED_AT = Instant.parse("2026-10-20T09:12:00Z");

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppAccessService service;

    @BeforeEach
    void setUp() {
        service = new AppAccessService(keycloakAdminClient, eventPublisher, TransactionOperations.withoutTransaction());
    }

    @Test
    void grantsTheGameCatalogToAnApprovedPerson() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(user(GUEST_ID, true, List.of("guest"))));

        service.grantApp(ADMIN_ID, GUEST_ID, GameCatalogAccess.APP_KEY);

        verify(keycloakAdminClient).grantRealmRole(GUEST_ID.toString(), "app-gamecatalog");
    }

    @Test
    void grantingAnAppThePersonAlreadyHasChangesNothing() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(user(GUEST_ID, true, List.of("guest", "app-gamecatalog"))));

        service.grantApp(ADMIN_ID, GUEST_ID, GameCatalogAccess.APP_KEY);

        verify(keycloakAdminClient, never()).grantRealmRole(anyString(), anyString());
    }

    @Test
    void grantingToAPendingPersonIsRefused() {
        when(keycloakAdminClient.findUser(PENDING_ID.toString()))
                .thenReturn(Optional.of(user(PENDING_ID, true, List.of())));

        assertThatThrownBy(() -> service.grantApp(ADMIN_ID, PENDING_ID, GameCatalogAccess.APP_KEY))
                .isInstanceOf(UserNotApprovedException.class);
        verify(keycloakAdminClient, never()).grantRealmRole(anyString(), anyString());
    }

    @Test
    void grantingToARecentlyRevokedPersonIsRefused() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(user(GUEST_ID, false, List.of("guest"))));

        assertThatThrownBy(() -> service.grantApp(ADMIN_ID, GUEST_ID, GameCatalogAccess.APP_KEY))
                .isInstanceOf(UserNotApprovedException.class);
        verify(keycloakAdminClient, never()).grantRealmRole(anyString(), anyString());
    }

    @Test
    void grantingToTheAdminsOwnAccountIsRefused() {
        assertThatThrownBy(() -> service.grantApp(ADMIN_ID, ADMIN_ID, GameCatalogAccess.APP_KEY))
                .isInstanceOf(CannotChangeOwnAccountException.class);
        verify(keycloakAdminClient, never()).findUser(anyString());
    }

    @Test
    void removingTheGameCatalogRevokesTheRoleAndRemovesTheVotes() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(user(GUEST_ID, true, List.of("guest", "app-gamecatalog"))));

        service.removeApp(ADMIN_ID, GUEST_ID, GameCatalogAccess.APP_KEY);

        verify(keycloakAdminClient).revokeRealmRole(GUEST_ID.toString(), "app-gamecatalog");
        verify(eventPublisher).publishEvent(new AppAccessRemoved(GUEST_ID.toString(), GameCatalogAccess.APP_KEY));
    }

    @Test
    void removingAccessToANonGrantableAppChangesNothing() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString()))
                .thenReturn(Optional.of(user(GUEST_ID, true, List.of("guest"))));

        service.removeApp(ADMIN_ID, GUEST_ID, ManagedApp.FNA.key());

        verify(keycloakAdminClient, never()).revokeRealmRole(anyString(), anyString());
        verify(eventPublisher, never()).publishEvent(new AppAccessRemoved(GUEST_ID.toString(), ManagedApp.FNA.key()));
    }

    @Test
    void removingAccessFromTheAdminsOwnAccountIsRefused() {
        assertThatThrownBy(() -> service.removeApp(ADMIN_ID, ADMIN_ID, GameCatalogAccess.APP_KEY))
                .isInstanceOf(CannotChangeOwnAccountException.class);
    }

    @Test
    void grantingAnUnknownAppIsRefusedBeforeTheUserIsLookedUp() {
        assertThatThrownBy(() -> service.grantApp(ADMIN_ID, GUEST_ID, "unknown"))
                .isInstanceOf(AppNotFoundException.class);
        verify(keycloakAdminClient, never()).findUser(anyString());
    }

    @Test
    void grantingANonGrantableAppIsRefusedBeforeTheUserIsLookedUp() {
        assertThatThrownBy(() -> service.grantApp(ADMIN_ID, GUEST_ID, ManagedApp.FNA.key()))
                .isInstanceOf(AppNotGrantableException.class);
        verify(keycloakAdminClient, never()).findUser(anyString());
    }

    @Test
    void unknownUsersAreRejected() {
        when(keycloakAdminClient.findUser(GUEST_ID.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantApp(ADMIN_ID, GUEST_ID, GameCatalogAccess.APP_KEY))
                .isInstanceOf(UserNotFoundException.class);
    }

    private KeycloakAdminClient.KeycloakUser user(UUID id, boolean enabled, List<String> roles) {
        return new KeycloakAdminClient.KeycloakUser(id.toString(), "friend@example.org", "Ada", "Palmer", CREATED_AT,
                enabled, roles);
    }
}
