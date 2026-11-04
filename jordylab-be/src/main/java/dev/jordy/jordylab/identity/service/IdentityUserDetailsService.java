package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

import static dev.jordy.jordylab.identity.domain.UserStatus.REJECTED;
import static dev.jordy.jordylab.identity.domain.UserStatus.REVOKED;

/**
 * The platform's {@link UserDetailsService} (spec 019 D16). Spring Security's WebAuthn provider loads the person after a
 * successful assertion through this service; returning an {@link dev.jordy.jordylab.identity.AuthenticatedUser} keeps
 * the session principal identical to a password sign-in. Rejected and revoked accounts are refused here, exactly like
 * the password flow (FR-016); a pending account resolves with no authorities.
 */
@Service
@RequiredArgsConstructor
public class IdentityUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        UserAccount account = userAccountRepository.findByUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        if (account.getStatus() == REJECTED) {
            throw new DisabledException("This account was rejected");
        }
        if (account.getStatus() == REVOKED) {
            throw new DisabledException("This account's access was revoked");
        }

        return SessionSignInService.toAuthenticatedUser(account);
    }
}
