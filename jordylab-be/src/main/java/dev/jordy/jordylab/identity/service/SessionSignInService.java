package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static dev.jordy.jordylab.identity.domain.UserStatus.REJECTED;
import static dev.jordy.jordylab.identity.domain.UserStatus.REVOKED;

/**
 * Username/password sign-in without Keycloak (spec 019 US1, D1/D9). Unknown usernames and wrong passwords are
 * indistinguishable; five consecutive failures lock the account for the configured window; rejected and revoked
 * accounts are refused with their own reason; pending accounts authenticate with no authority (the frontend routes
 * them to {@code /awaiting-approval}, exactly as before).
 */
@Service
@RequiredArgsConstructor
public class SessionSignInService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final Clock clock;

    @Transactional
    public Authentication signIn(String rawUsername, String rawPassword) {
        Instant now = clock.instant();
        UserAccount account = userAccountRepository.findByUsername(normalize(rawUsername))
                .orElseThrow(InvalidCredentialsException::new);
        if (account.isLocked(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            account.recordFailedSignIn(now, properties.lockout().maxFailures(),
                    Duration.ofMinutes(properties.lockout().lockMinutes()));
            userAccountRepository.save(account);
            throw new InvalidCredentialsException();
        }
        if (account.getStatus() == REJECTED) {
            throw new AccountRejectedException();
        }
        if (account.getStatus() == REVOKED) {
            throw new AccountRevokedException();
        }
        account.recordSuccessfulSignIn();
        userAccountRepository.save(account);

        return new UsernamePasswordAuthenticationToken(toAuthenticatedUser(account), null, authoritiesOf(account));
    }

    /** The public identity of an account, as the session principal carries it. */
    public static AuthenticatedUser toAuthenticatedUser(UserAccount account) {
        return new AuthenticatedUser(account.getId(), account.getUsername(), account.roleKey().orElse(null),
                Set.copyOf(account.getAppGrants()));
    }

    /**
     * Authorities for a session: an approved admin gets {@code ROLE_admin}; a guest gets {@code ROLE_guest} plus
     * {@code ROLE_app-gamecatalog} per grant; a pending account gets none.
     */
    public static List<GrantedAuthority> authoritiesOf(UserAccount account) {
        if (account.getStatus() != UserStatus.APPROVED) {
            return List.of();
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        account.roleKey().ifPresent(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        account.getAppGrants().forEach(app -> app.grantRole()
                .ifPresent(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role))));

        return authorities;
    }

    private static String normalize(String rawUsername) {
        if (rawUsername == null) {
            return "";
        }

        return rawUsername.trim().toLowerCase(Locale.ROOT);
    }
}
