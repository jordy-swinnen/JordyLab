package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.UserAccessRemoved;
import dev.jordy.jordylab.identity.UserDeleted;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Deletes a non-admin account and everything tied to it (spec 019 US4, parity with spec 014): its web sessions are
 * ended, the access events let other modules clean their per-person data (marks, guest usage and limits), and the
 * account row goes. Refuses the caller's own account and any admin.
 */
@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private final UserAccountRepository userAccountRepository;
    private final UserSessionService userSessionService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void delete(UUID callerId, UUID targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new OwnAccountOperationException();
        }
        UserAccount target = userAccountRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
        if (target.getRole() == UserRole.ADMIN) {
            throw new AdminAccountProtectedException();
        }
        userSessionService.endAllFor(target);
        eventPublisher.publishEvent(new UserAccessRemoved(target.getId().toString()));
        eventPublisher.publishEvent(new UserDeleted(target.getId().toString()));
        userAccountRepository.delete(target);
    }
}
