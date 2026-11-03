package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The WebAuthn provider resolves the session principal through this service (spec 019 D16, T038). */
class IdentityUserDetailsServiceTest {

    private static final UUID USER_ID = UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final IdentityUserDetailsService service = new IdentityUserDetailsService(userAccountRepository);

    @Test
    void loadsAnApprovedAccountAsTheSessionPrincipal() {
        when(userAccountRepository.findByUsername("jordy")).thenReturn(Optional.of(account(UserStatus.APPROVED)));

        UserDetails details = service.loadUserByUsername("  Jordy ");

        assertThat(details).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser user = (AuthenticatedUser) details;
        assertSoftly(softly -> {
            softly.assertThat(user.id()).isEqualTo(USER_ID);
            softly.assertThat(user.username()).isEqualTo("jordy");
            softly.assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_guest", "ROLE_app-gamecatalog");
        });
    }

    @Test
    void loadsAPendingAccountWithoutAuthorities() {
        when(userAccountRepository.findByUsername("jordy")).thenReturn(Optional.of(pendingAccount()));

        UserDetails details = service.loadUserByUsername("jordy");

        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    void refusesRejectedAndRevokedAccounts() {
        when(userAccountRepository.findByUsername("jordy")).thenReturn(Optional.of(account(UserStatus.REJECTED)));

        assertThatThrownBy(() -> service.loadUserByUsername("jordy")).isInstanceOf(DisabledException.class);

        when(userAccountRepository.findByUsername("jordy")).thenReturn(Optional.of(account(UserStatus.REVOKED)));

        assertThatThrownBy(() -> service.loadUserByUsername("jordy")).isInstanceOf(DisabledException.class);
    }

    @Test
    void refusesUnknownAccounts() {
        when(userAccountRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private static UserAccount account(UserStatus status) {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username("jordy")
                .status(status)
                .role(status == UserStatus.APPROVED ? UserRole.GUEST : null)
                .appGrants(status == UserStatus.APPROVED ? Set.of(ManagedApp.GAME_CATALOG) : Set.of())
                .build();
        account.applyPassword("{bcrypt}$2a$10$hash");

        return account;
    }

    private static UserAccount pendingAccount() {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username("jordy")
                .status(UserStatus.PENDING)
                .build();
        account.applyPassword("{bcrypt}$2a$10$hash");

        return account;
    }
}
