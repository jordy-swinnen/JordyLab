package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.rest.client.KeycloakAdminClient;
import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A person's active sessions, and ending one or all of them (spec 014 US5, FR-018 to FR-020).
 *
 * <p>Offline sessions are the ones opened for {@code gamecatalog-script} by the game-library scanner (device-code login
 * with {@code offline_access}); a normal logout does not end them. Which list a session came from decides how it is
 * ended, so the service looks the session up rather than trusting the panel. The phone app's own offline token was
 * removed with the native app (spec 018).
 */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    public enum SessionKind {
        WEB, SCANNER
    }

    public record PersonSession(String id, SessionKind kind, boolean offline, Instant startedAt,
            Instant lastAccessedAt) {
    }

    private static final String SCANNER_CLIENT_ID = "gamecatalog-script";

    private final KeycloakAdminClient keycloakAdminClient;

    public List<PersonSession> listSessions(UUID callerId, UUID targetUserId) {
        refuseOwnAccount(callerId, targetUserId);
        requireUser(targetUserId);

        List<PersonSession> sessions = new ArrayList<>();
        for (KeycloakAdminClient.KeycloakSession session : regularSessions(targetUserId.toString())) {
            sessions.add(toPersonSession(session, false));
        }
        for (KeycloakAdminClient.KeycloakSession session : offlineSessions(targetUserId.toString())) {
            sessions.add(toPersonSession(session, true));
        }

        return sessions;
    }

    public void endSession(UUID callerId, UUID targetUserId, String sessionId) {
        refuseOwnAccount(callerId, targetUserId);
        requireUser(targetUserId);
        String subject = targetUserId.toString();

        boolean offline = offlineSessions(subject).stream()
                .anyMatch(session -> session.id().equals(sessionId));
        boolean regular = !offline && regularSessions(subject).stream()
                .anyMatch(session -> session.id().equals(sessionId));
        if (!offline && !regular) {
            throw new UserSessionNotFoundException(sessionId);
        }
        keycloakAdminClient.deleteSession(sessionId, offline);
    }

    /** FR-019: ending all includes the scanner's offline sessions, which a plain logout leaves alive. */
    public void endAllSessions(UUID callerId, UUID targetUserId) {
        refuseOwnAccount(callerId, targetUserId);
        requireUser(targetUserId);
        String subject = targetUserId.toString();
        keycloakAdminClient.endSessions(subject);
        for (KeycloakAdminClient.KeycloakSession offlineSession : offlineSessions(subject)) {
            keycloakAdminClient.deleteSession(offlineSession.id(), true);
        }
    }

    /** Shown next to each person in the list; {@code null} when the identity provider cannot answer for this one. */
    public Integer countFor(UUID targetUserId) {
        try {
            String subject = targetUserId.toString();

            return regularSessions(subject).size() + offlineSessions(subject).size();
        } catch (KeycloakUnavailableException unavailable) {
            return null;
        }
    }

    private List<KeycloakAdminClient.KeycloakSession> regularSessions(String subject) {
        return keycloakAdminClient.listUserSessions(subject);
    }

    private List<KeycloakAdminClient.KeycloakSession> offlineSessions(String subject) {
        Optional<String> scannerClientUuid = keycloakAdminClient.findClientUuid(SCANNER_CLIENT_ID);
        if (scannerClientUuid.isEmpty()) {
            return List.of();
        }

        return keycloakAdminClient.listOfflineSessions(subject, scannerClientUuid.get());
    }

    private PersonSession toPersonSession(KeycloakAdminClient.KeycloakSession session, boolean offline) {
        boolean scanner = offline || session.clientIds().contains(SCANNER_CLIENT_ID);

        return new PersonSession(session.id(), scanner ? SessionKind.SCANNER : SessionKind.WEB, offline,
                session.startedAt(), session.lastAccessedAt());
    }

    private void refuseOwnAccount(UUID callerId, UUID targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new CannotChangeOwnAccountException(targetUserId);
        }
    }

    private void requireUser(UUID targetUserId) {
        keycloakAdminClient.findUser(targetUserId.toString())
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
    }
}
