package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionSignInServiceTest {

    private static final UUID USER_ID = UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");
    private static final String USERNAME = "jordy";
    private static final String PASSWORD = "correct horse battery";
    private static final String ENCODED = "{bcrypt}$2a$10$encoded";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final SessionSignInService service = new SessionSignInService(userAccountRepository, passwordEncoder,
            new IdentityProperties(
                    new IdentityProperties.Password(12),
                    new IdentityProperties.Lockout(5, 15),
                    new IdentityProperties.Bootstrap("", ""),
                    new IdentityProperties.WebAuthn("localhost", "JordyLab", List.of("http://localhost:4200"))),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void stubAccountLookup() {
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(approvedGuest()));
        when(userAccountRepository.findByUsername("unknown")).thenReturn(Optional.empty());
    }

    @Test
    void unknownUsernameFailsUniformly() {
        assertThatThrownBy(() -> service.signIn("unknown", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongPasswordFailsUniformlyAndCountsTheAttempt() {
        UserAccount account = approvedGuest();
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> service.signIn(USERNAME, "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(account.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void successfulSignInCarriesRoleAndGrantAuthorities() {
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        Authentication authentication = service.signIn(USERNAME, PASSWORD);

        assertSoftly(softly -> {
            softly.assertThat(authentication.getPrincipal()).isEqualTo(
                    new AuthenticatedUser(USER_ID, USERNAME, "guest", Set.of(ManagedApp.GAME_CATALOG)));
            softly.assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_guest", "ROLE_app-gamecatalog");
        });
    }

    @Test
    void adminGetsTheAdminAuthority() {
        UserAccount admin = approvedAccount(UserRole.ADMIN, Set.of());
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        Authentication authentication = service.signIn(USERNAME, PASSWORD);

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_admin");
    }

    @Test
    void pendingAccountSignsInWithoutAuthorities() {
        UserAccount pending = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.PENDING)
                .build();
        pending.applyPassword(ENCODED);
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        Authentication authentication = service.signIn(USERNAME, PASSWORD);

        assertSoftly(softly -> {
            softly.assertThat(authentication.getAuthorities()).isEmpty();
            softly.assertThat(authentication.getPrincipal())
                    .isEqualTo(new AuthenticatedUser(USER_ID, USERNAME, null, Set.of()));
        });
    }

    @Test
    void rejectedAccountIsRefused() {
        UserAccount rejected = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.REJECTED)
                .build();
        rejected.applyPassword(ENCODED);
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(rejected));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        assertThatThrownBy(() -> service.signIn(USERNAME, PASSWORD))
                .isInstanceOf(AccountRejectedException.class);
    }

    @Test
    void revokedAccountIsRefused() {
        UserAccount revoked = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.REVOKED)
                .build();
        revoked.applyPassword(ENCODED);
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(revoked));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        assertThatThrownBy(() -> service.signIn(USERNAME, PASSWORD))
                .isInstanceOf(AccountRevokedException.class);
    }

    @Test
    void migratedAccountWithoutAPasswordCannotSignIn() {
        UserAccount migrated = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.APPROVED)
                .role(UserRole.GUEST)
                .build();
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(migrated));

        assertThatThrownBy(() -> service.signIn(USERNAME, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void fiveFailuresLockTheAccountForTheConfiguredWindow() {
        UserAccount account = approvedGuest();
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", ENCODED)).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.signIn(USERNAME, "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> service.signIn(USERNAME, "wrong"))
                .isInstanceOfSatisfying(AccountLockedException.class,
                        locked -> assertThat(locked.getLockedUntil())
                                .isEqualTo(NOW.plus(Duration.ofMinutes(15))));
    }

    @Test
    void lockedAccountIsRefusedEvenWithTheRightPassword() {
        UserAccount account = approvedGuest();
        account.recordFailedSignIn(NOW, 1, Duration.ofMinutes(15));
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        assertThatThrownBy(() -> service.signIn(USERNAME, PASSWORD))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void successfulSignInClearsFailureCounters() {
        UserAccount account = approvedGuest();
        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);

        service.signIn(USERNAME, PASSWORD);

        assertSoftly(softly -> {
            softly.assertThat(account.getFailedAttempts()).isZero();
            softly.assertThat(account.getLockedUntil()).isNull();
        });
    }

    private static UserAccount approvedGuest() {
        return approvedAccount(UserRole.GUEST, Set.of(ManagedApp.GAME_CATALOG));
    }

    private static UserAccount approvedAccount(UserRole role, Set<ManagedApp> grants) {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.APPROVED)
                .role(role)
                .appGrants(grants)
                .build();
        account.applyPassword(ENCODED);

        return account;
    }
}
