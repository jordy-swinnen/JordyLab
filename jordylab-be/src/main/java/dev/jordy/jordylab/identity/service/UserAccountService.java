package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.shared.rest.client.NtfyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Account lifecycle without Keycloak (spec 019 US1/US4, D7/D17). Registration notifies the admin through the shared
 * ntfy client (replacing the old pending-signup poller); approval applies the {@code guest} role and the default app
 * grants; reject/revoke publish {@code UserAccessRemoved} through the entity's registered events; the last approved
 * admin can never be removed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserAccountService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9._-]{3,40}");
    private static final String NOTIFICATION_TITLE = "New JordyLab sign-up";
    private static final String USERS_SCREEN_PATH = "/settings/users";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final NtfyClient ntfyClient;

    /** Creates a pending account; no email is asked for and the password is stored only as a BCrypt hash. */
    @Transactional
    public UserAccount register(String rawUsername, String rawPassword, String rawDisplayName) {
        String username = normalize(rawUsername);
        validateUsername(username);
        validatePassword(rawPassword, username);
        if (userAccountRepository.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        UserAccount account = UserAccount.builder()
                .username(username)
                .displayName(displayNameOrNull(rawDisplayName))
                .status(UserStatus.PENDING)
                .build();
        account.applyPassword(passwordEncoder.encode(rawPassword));
        UserAccount saved = userAccountRepository.save(account);
        notifyAdmins(saved);

        return saved;
    }

    /** Approves with the given role (guest by default) and re-applies the default app grants (spec 014 FR-008). */
    @Transactional
    public UserAccount approve(UUID userId, UserRole requestedRole) {
        UserAccount account = require(userId);
        if (account.isApproved()) {
            throw new AlreadyApprovedException(userId);
        }
        account.approve(requestedRole == null ? UserRole.GUEST : requestedRole);

        return userAccountRepository.save(account);
    }

    @Transactional
    public UserAccount reject(UUID userId) {
        UserAccount account = require(userId);
        guardLastAdmin(account);
        account.reject();

        return userAccountRepository.save(account);
    }

    @Transactional
    public UserAccount revoke(UUID userId) {
        UserAccount account = require(userId);
        if (!account.isApproved()) {
            throw new UserNotApprovedException();
        }
        guardLastAdmin(account);
        account.revoke();

        return userAccountRepository.save(account);
    }

    /** Grants one app to an approved account (spec 019 US4, parity with spec 014 FR-002). */
    @Transactional
    public UserAccount grantApp(UUID userId, ManagedApp app) {
        UserAccount account = requireApproved(userId, app);
        account.grantApp(app);

        return userAccountRepository.save(account);
    }

    /** Revokes one app; the removed app's data is cleaned up by its event listener. */
    @Transactional
    public UserAccount revokeApp(UUID userId, ManagedApp app) {
        UserAccount account = requireApproved(userId, app);
        account.revokeApp(app);

        return userAccountRepository.save(account);
    }

    private UserAccount requireApproved(UUID userId, ManagedApp app) {
        if (app == null || !app.grantable()) {
            throw new InvalidRequestException("That app cannot be granted");
        }
        UserAccount account = require(userId);
        if (!account.isApproved()) {
            throw new UserNotApprovedException();
        }

        return account;
    }

    /** The signed-in user changes their own password; the current one must be confirmed (spec 019 FR-005). */
    @Transactional
    public void changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount account = require(userId);
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        validatePassword(newPassword, account.getUsername());
        account.applyPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(account);
    }

    @Transactional
    public UserAccount changeOwnDisplayName(UUID userId, String newDisplayName) {
        UserAccount account = require(userId);
        account.changeDisplayName(newDisplayName);

        return userAccountRepository.save(account);
    }

    public List<UserAccount> listUsers(UserStatus statusFilter) {
        if (statusFilter == null) {
            return userAccountRepository.findAllByOrderByCreatedDateDesc();
        }

        return userAccountRepository.findByStatusOrderByCreatedDateDesc(statusFilter);
    }

    public long pendingCount() {
        return userAccountRepository.findByStatusOrderByCreatedDateDesc(UserStatus.PENDING).size();
    }

    private UserAccount require(UUID userId) {
        return userAccountRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void guardLastAdmin(UserAccount target) {
        if (target.getRole() != UserRole.ADMIN || !target.isApproved()) {
            return;
        }
        long approvedAdmins = userAccountRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.APPROVED);
        if (approvedAdmins <= 1) {
            throw new LastAdminProtectedException();
        }
    }

    private void notifyAdmins(UserAccount account) {
        String who = StringUtils.hasText(account.getDisplayName())
                ? account.getDisplayName() + " (" + account.getUsername() + ")"
                : account.getUsername();
        ntfyClient.publish(NOTIFICATION_TITLE, who + " is waiting for approval.", USERS_SCREEN_PATH);
    }

    private static void validateUsername(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new InvalidUsernameException(
                    "Usernames are 3 to 40 characters: lowercase letters, digits, dot, dash or underscore");
        }
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

    private static String normalize(String rawUsername) {
        if (rawUsername == null) {
            throw new InvalidUsernameException("A username is required");
        }

        return rawUsername.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayNameOrNull(String rawDisplayName) {
        if (!StringUtils.hasText(rawDisplayName)) {
            return null;
        }

        return rawDisplayName.trim();
    }
}
