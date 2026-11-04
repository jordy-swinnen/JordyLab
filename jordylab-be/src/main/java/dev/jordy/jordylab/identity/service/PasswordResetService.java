package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.domain.PasswordResetRequest;
import dev.jordy.jordylab.identity.domain.ResetRequestStatus;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.repository.PasswordResetRequestRepository;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.util.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The no-email password recovery flow (spec 019 US6, D15). A locked-out user submits a new password while signed out;
 * only its BCrypt hash is stored, one pending request per account. An admin approves (the requested password becomes
 * active) or rejects it (the old one stays). An admin direct reset issues a generated temporary password shown once,
 * and a self-service change supersedes any pending request. Unknown usernames leave no trace and get the same reply.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * Accepts a reset request for an existing account. Always succeeds quietly for unknown usernames; only a weak new
     * password is rejected. The hash is computed before the lookup so the reply takes the same shape and effort either
     * way (no account enumeration).
     */
    @Transactional
    public void request(String rawUsername, String newPassword) {
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase(Locale.ROOT);
        validatePassword(newPassword, username);
        String requestedHash = passwordEncoder.encode(newPassword);
        if (!StringUtils.hasText(username)) {
            return;
        }
        userAccountRepository.findByUsername(username).ifPresent(account -> {
            passwordResetRequestRepository.deleteByUserAccountIdAndStatus(account.getId(),
                    ResetRequestStatus.PENDING);
            passwordResetRequestRepository.save(PasswordResetRequest.builder()
                    .userAccountId(account.getId())
                    .requestedPasswordHash(requestedHash)
                    .build());
        });
    }

    /** Applies the user's requested password; the request must still be pending. */
    @Transactional
    public void approve(UUID adminId, UUID userId, UUID requestId) {
        PasswordResetRequest request = requirePending(userId, requestId);
        UserAccount account = requireUser(userId);
        account.applyPassword(request.getRequestedPasswordHash());
        request.approve(adminId, clock.instant());
        userAccountRepository.save(account);
        passwordResetRequestRepository.save(request);
    }

    /** Closes a pending request without touching the account; the old password keeps working. */
    @Transactional
    public void reject(UUID adminId, UUID userId, UUID requestId) {
        PasswordResetRequest request = requirePending(userId, requestId);
        request.reject(adminId, clock.instant());
        passwordResetRequestRepository.save(request);
    }

    /** Admin direct reset: a generated password, shown once, that supersedes any pending request. */
    @Transactional
    public String resetDirectly(UUID userId) {
        UserAccount account = requireUser(userId);
        String temporaryPassword = TemporaryPasswordGenerator.generate();
        account.applyPassword(passwordEncoder.encode(temporaryPassword));
        userAccountRepository.save(account);
        passwordResetRequestRepository.deleteByUserAccountIdAndStatus(userId, ResetRequestStatus.PENDING);

        return temporaryPassword;
    }

    /** Pending requests keyed by account id, for the admin's user list. */
    public Map<UUID, PasswordResetRequest> pendingByUser() {
        return passwordResetRequestRepository.findByStatus(ResetRequestStatus.PENDING).stream()
                .collect(Collectors.toMap(PasswordResetRequest::getUserAccountId, Function.identity()));
    }

    private PasswordResetRequest requirePending(UUID userId, UUID requestId) {
        PasswordResetRequest request = passwordResetRequestRepository.findById(requestId)
                .filter(candidate -> candidate.getUserAccountId().equals(userId))
                .orElseThrow(ResetRequestNotFoundException::new);
        if (!request.isPending()) {
            throw new ResetRequestNotPendingException();
        }

        return request;
    }

    private UserAccount requireUser(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void validatePassword(String password, String username) {
        int minLength = properties.password().minLength();
        if (!StringUtils.hasText(password) || password.length() < minLength) {
            throw new WeakPasswordException("The password must be at least " + minLength + " characters");
        }
        if (password.equalsIgnoreCase(username)) {
            throw new WeakPasswordException("The password must not equal the username");
        }
    }
}
