package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<Session> sessions = mock(FindByIndexNameSessionRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserSessionService service = new UserSessionService(sessions, userAccountRepository);

    @Test
    void listsTheTargetsWebSessions() {
        givenTarget();
        Session session = mock(Session.class);
        Instant now = Instant.parse("2026-11-03T12:00:00Z");
        when(session.getId()).thenReturn("session-1");
        when(session.getCreationTime()).thenReturn(now.minusSeconds(60));
        when(session.getLastAccessedTime()).thenReturn(now);
        when(sessions.findByPrincipalName("guest-account")).thenReturn(Map.of("session-1", session));

        List<UserSessionService.SessionInfo> listed = service.list(ADMIN_ID, TARGET_ID);

        assertThat(listed).containsExactly(
                new UserSessionService.SessionInfo("session-1", now.minusSeconds(60), now));
    }

    @Test
    void refusesTheCallersOwnAccount() {
        assertThatThrownBy(() -> service.endAll(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(OwnAccountOperationException.class);
        verify(sessions, never()).findByPrincipalName("admin");
    }

    @Test
    void endsOnlySessionsOfTheTarget() {
        givenTarget();
        when(sessions.findByPrincipalName("guest-account")).thenReturn(Map.of("session-1", mock(Session.class)));

        service.endOne(ADMIN_ID, TARGET_ID, "session-1");

        verify(sessions).deleteById("session-1");
    }

    @Test
    void refusesASessionThatIsNotTheTargets() {
        givenTarget();
        when(sessions.findByPrincipalName("guest-account")).thenReturn(Map.of());

        assertThatThrownBy(() -> service.endOne(ADMIN_ID, TARGET_ID, "other-session"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void endsEverySessionOfTheTarget() {
        givenTarget();
        when(sessions.findByPrincipalName("guest-account"))
                .thenReturn(Map.of("s1", mock(Session.class), "s2", mock(Session.class)));

        service.endAll(ADMIN_ID, TARGET_ID);

        verify(sessions).deleteById("s1");
        verify(sessions).deleteById("s2");
    }

    private void givenTarget() {
        UserAccount account = UserAccount.builder()
                .id(TARGET_ID)
                .username("guest-account")
                .status(UserStatus.APPROVED)
                .role(UserRole.GUEST)
                .build();
        account.applyPassword("{bcrypt}$2a$10$hash");
        when(userAccountRepository.findById(TARGET_ID)).thenReturn(Optional.of(account));
    }
}
