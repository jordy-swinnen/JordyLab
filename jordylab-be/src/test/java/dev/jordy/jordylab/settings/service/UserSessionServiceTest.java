package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String SUBJECT = GUEST_ID.toString();
    private static final String SCANNER_UUID = "mobile-client-uuid";
    private static final Instant STARTED = Instant.parse("2026-11-02T08:01:00Z");
    private static final Instant LAST_ACCESS = Instant.parse("2026-11-02T08:40:00Z");

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    private UserSessionService service;

    @BeforeEach
    void setUp() {
        service = new UserSessionService(keycloakAdminClient);
    }

    @Test
    void listsWebAndScannerSessionsWithTheirKind() {
        when(keycloakAdminClient.findUser(SUBJECT)).thenReturn(Optional.of(user()));
        when(keycloakAdminClient.listUserSessions(SUBJECT)).thenReturn(List.of(
                session("web-1", List.of("jordylab-host")),
                session("scanner-online", List.of("gamecatalog-script"))));
        when(keycloakAdminClient.findClientUuid("gamecatalog-script")).thenReturn(Optional.of(SCANNER_UUID));
        when(keycloakAdminClient.listOfflineSessions(SUBJECT, SCANNER_UUID))
                .thenReturn(List.of(session("scanner-offline", List.of("gamecatalog-script"))));

        List<UserSessionService.PersonSession> sessions = service.listSessions(ADMIN_ID, GUEST_ID);

        assertSoftly(softly -> {
            softly.assertThat(sessions).hasSize(3);
            softly.assertThat(sessions.get(0).kind()).isEqualTo(UserSessionService.SessionKind.WEB);
            softly.assertThat(sessions.get(0).offline()).isFalse();
            softly.assertThat(sessions.get(1).kind()).isEqualTo(UserSessionService.SessionKind.SCANNER);
            softly.assertThat(sessions.get(2).kind()).isEqualTo(UserSessionService.SessionKind.SCANNER);
            softly.assertThat(sessions.get(2).offline()).isTrue();
        });
    }

    @Test
    void endingOneWebSessionUsesTheRegularDelete() {
        when(keycloakAdminClient.findUser(SUBJECT)).thenReturn(Optional.of(user()));
        when(keycloakAdminClient.findClientUuid("gamecatalog-script")).thenReturn(Optional.of(SCANNER_UUID));
        when(keycloakAdminClient.listOfflineSessions(SUBJECT, SCANNER_UUID)).thenReturn(List.of());
        when(keycloakAdminClient.listUserSessions(SUBJECT)).thenReturn(List.of(session("web-1", List.of("jordylab-host"))));

        service.endSession(ADMIN_ID, GUEST_ID, "web-1");

        verify(keycloakAdminClient).deleteSession("web-1", false);
    }

    @Test
    void endingOneScannerOfflineSessionUsesTheOfflineDelete() {
        when(keycloakAdminClient.findUser(SUBJECT)).thenReturn(Optional.of(user()));
        when(keycloakAdminClient.findClientUuid("gamecatalog-script")).thenReturn(Optional.of(SCANNER_UUID));
        when(keycloakAdminClient.listOfflineSessions(SUBJECT, SCANNER_UUID))
                .thenReturn(List.of(session("scanner-1", List.of("gamecatalog-script"))));

        service.endSession(ADMIN_ID, GUEST_ID, "scanner-1");

        verify(keycloakAdminClient).deleteSession("scanner-1", true);
    }

    @Test
    void anUnknownSessionIsRejected() {
        when(keycloakAdminClient.findUser(SUBJECT)).thenReturn(Optional.of(user()));
        when(keycloakAdminClient.findClientUuid("gamecatalog-script")).thenReturn(Optional.of(SCANNER_UUID));
        when(keycloakAdminClient.listOfflineSessions(SUBJECT, SCANNER_UUID)).thenReturn(List.of());
        when(keycloakAdminClient.listUserSessions(SUBJECT)).thenReturn(List.of());

        assertThatThrownBy(() -> service.endSession(ADMIN_ID, GUEST_ID, "nope"))
                .isInstanceOf(UserSessionNotFoundException.class);
        verify(keycloakAdminClient, never()).deleteSession("nope", false);
    }

    @Test
    void endingAllLogsOutAndEndsTheScannerOfflineSession() {
        when(keycloakAdminClient.findUser(SUBJECT)).thenReturn(Optional.of(user()));
        when(keycloakAdminClient.findClientUuid("gamecatalog-script")).thenReturn(Optional.of(SCANNER_UUID));
        when(keycloakAdminClient.listOfflineSessions(SUBJECT, SCANNER_UUID))
                .thenReturn(List.of(session("scanner-1", List.of("gamecatalog-script"))));

        service.endAllSessions(ADMIN_ID, GUEST_ID);

        verify(keycloakAdminClient).endSessions(SUBJECT);
        verify(keycloakAdminClient).deleteSession("scanner-1", true);
    }

    @Test
    void endingAllIsFineWhenTheScannerClientIsNotInTheRealm() {
        when(keycloakAdminClient.findUser(SUBJECT)).thenReturn(Optional.of(user()));
        when(keycloakAdminClient.findClientUuid("gamecatalog-script")).thenReturn(Optional.empty());

        service.endAllSessions(ADMIN_ID, GUEST_ID);

        verify(keycloakAdminClient).endSessions(SUBJECT);
    }

    @Test
    void theAdminCannotManageTheirOwnSessions() {
        assertThatThrownBy(() -> service.listSessions(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(CannotChangeOwnAccountException.class);
        verify(keycloakAdminClient, never()).findUser(ADMIN_ID.toString());
    }

    @Test
    void theCountSumsRegularAndOfflineSessions() {
        when(keycloakAdminClient.listUserSessions(SUBJECT)).thenReturn(List.of(session("web-1", List.of("jordylab-host"))));
        when(keycloakAdminClient.findClientUuid("gamecatalog-script")).thenReturn(Optional.of(SCANNER_UUID));
        when(keycloakAdminClient.listOfflineSessions(SUBJECT, SCANNER_UUID))
                .thenReturn(List.of(session("scanner-1", List.of("gamecatalog-script"))));

        assertSoftly(softly -> softly.assertThat(service.countFor(GUEST_ID)).isEqualTo(2));
    }

    @Test
    void theCountIsUnknownWhenTheIdentityProviderCannotAnswer() {
        when(keycloakAdminClient.listUserSessions(SUBJECT))
                .thenThrow(new KeycloakUnavailableException("down", null));

        assertSoftly(softly -> softly.assertThat(service.countFor(GUEST_ID)).isNull());
    }

    private KeycloakAdminClient.KeycloakUser user() {
        return new KeycloakAdminClient.KeycloakUser(SUBJECT, "friend@example.org", "Ada", "Palmer",
                Instant.parse("2026-10-20T09:12:00Z"), true, List.of("guest"));
    }

    private KeycloakAdminClient.KeycloakSession session(String id, List<String> clients) {
        return new KeycloakAdminClient.KeycloakSession(id, STARTED, LAST_ACCESS, clients);
    }
}
