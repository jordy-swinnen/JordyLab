package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The admin's session panel (spec 019 US4, parity with spec 014): list an account's web sessions and end one or all.
 * Sessions are rows in Spring Session JDBC, looked up by the username stored as the session's principal name; the admin
 * cannot act on their own account (spec 014 FR-014).
 */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final UserAccountRepository userAccountRepository;

    public List<SessionInfo> list(UUID callerId, UUID targetUserId) {
        String username = requireTarget(callerId, targetUserId).getUsername();

        return sessions.findByPrincipalName(username).values().stream()
                .map(session -> new SessionInfo(session.getId(), session.getCreationTime(),
                        session.getLastAccessedTime()))
                .toList();
    }

    public void endOne(UUID callerId, UUID targetUserId, String sessionId) {
        String username = requireTarget(callerId, targetUserId).getUsername();
        if (!sessions.findByPrincipalName(username).containsKey(sessionId)) {
            throw new SessionNotFoundException();
        }
        sessions.deleteById(sessionId);
    }

    public void endAll(UUID callerId, UUID targetUserId) {
        endAllFor(requireTarget(callerId, targetUserId));
    }

    /** Used by the deletion flow, which has already applied its own guards. */
    void endAllFor(UserAccount account) {
        sessions.findByPrincipalName(account.getUsername()).keySet().forEach(sessions::deleteById);
    }

    private UserAccount requireTarget(UUID callerId, UUID targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new OwnAccountOperationException();
        }

        return userAccountRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
    }

    public record SessionInfo(String sessionId, Instant startedAt, Instant lastAccessedAt) {
    }
}
