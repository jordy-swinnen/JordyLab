package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.PasswordResetRequestRepository;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.shared.rest.client.NtfyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class UserAccountServiceTest {

    private static final UUID USER_ID = UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");
    private static final String USERNAME = "jordy";
    private static final String PASSWORD = "correct horse battery";
    private static final String ENCODED = "{bcrypt}$2a$10$encoded";

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final PasswordResetRequestRepository passwordResetRequestRepository =
            mock(PasswordResetRequestRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final NtfyClient ntfyClient = mock(NtfyClient.class);

    private final UserAccountService service = new UserAccountService(userAccountRepository,
            passwordResetRequestRepository, passwordEncoder,
            new IdentityProperties(
                    new IdentityProperties.Password(12),
                    new IdentityProperties.Lockout(5, 15),
                    new IdentityProperties.Bootstrap("", ""),
                    new IdentityProperties.WebAuthn("localhost", "JordyLab", List.of("http://localhost:4200"))),
            ntfyClient);

    @BeforeEach
    void stubEncoder() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED);
    }

    @Test
    void registerNormalisesAndStoresAPendingAccount() {
        when(userAccountRepository.existsByUsername(USERNAME)).thenReturn(false);
        org.mockito.ArgumentCaptor<UserAccount> savedAccount =
                org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        when(userAccountRepository.save(savedAccount.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register("  Jordy ", PASSWORD, " Jordy Swinnen ");

        UserAccount account = savedAccount.getValue();
        assertSoftly(softly -> {
            softly.assertThat(account.getUsername()).isEqualTo(USERNAME);
            softly.assertThat(account.getDisplayName()).isEqualTo("Jordy Swinnen");
            softly.assertThat(account.getStatus()).isEqualTo(UserStatus.PENDING);
            softly.assertThat(account.getPasswordHash()).isEqualTo(ENCODED);
            softly.assertThat(account.getRole()).isNull();
        });
        verify(ntfyClient).publish("New JordyLab sign-up",
                "Jordy Swinnen (jordy) is waiting for approval.", "/settings/users");
    }

    @Test
    void registerRejectsATakenUsername() {
        when(userAccountRepository.existsByUsername(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> service.register(USERNAME, PASSWORD, null))
                .isInstanceOf(UsernameTakenException.class);
        verify(userAccountRepository).existsByUsername(USERNAME);
        verifyNoMoreInteractions(userAccountRepository);
    }

    @Test
    void registerRejectsAShortPassword() {
        assertThatThrownBy(() -> service.register(USERNAME, "short", null))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void registerRejectsAPasswordEqualToTheUsername() {
        assertThatThrownBy(() -> service.register("jordy.jordy.jordy", "Jordy.Jordy.Jordy", null))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void registerRejectsMalformedUsernames() {
        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> service.register("ab", PASSWORD, null))
                    .isInstanceOf(InvalidUsernameException.class);
            softly.assertThatThrownBy(() -> service.register("has space", PASSWORD, null))
                    .isInstanceOf(InvalidUsernameException.class);
            softly.assertThatThrownBy(() -> service.register(null, PASSWORD, null))
                    .isInstanceOf(InvalidUsernameException.class);
        });
    }

    @Test
    void approveGrantsGuestAndTheDefaultApps() {
        UserAccount pending = pendingAccount();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(pending));
        when(userAccountRepository.save(pending)).thenReturn(pending);

        UserAccount approved = service.approve(USER_ID, null);

        assertSoftly(softly -> {
            softly.assertThat(approved.getStatus()).isEqualTo(UserStatus.APPROVED);
            softly.assertThat(approved.getRole()).isEqualTo(UserRole.GUEST);
            softly.assertThat(approved.getAppGrants()).containsExactly(ManagedApp.GAME_CATALOG);
        });
    }

    @Test
    void approveWithAdminRole() {
        UserAccount pending = pendingAccount();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(pending));
        when(userAccountRepository.save(pending)).thenReturn(pending);

        UserAccount approved = service.approve(USER_ID, UserRole.ADMIN);

        assertThat(approved.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void approveAnApprovedAccountFails() {
        UserAccount approved = approvedGuest();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approve(USER_ID, null)).isInstanceOf(AlreadyApprovedException.class);
        verify(userAccountRepository, never()).save(approved);
    }

    @Test
    void rejectMarksTheAccountRejected() {
        UserAccount pending = pendingAccount();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(pending));
        when(userAccountRepository.save(pending)).thenReturn(pending);

        UserAccount rejected = service.reject(USER_ID);

        assertThat(rejected.getStatus()).isEqualTo(UserStatus.REJECTED);
    }

    @Test
    void rejectRefusesTheLastAdmin() {
        UserAccount admin = approvedAdmin();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(admin));
        when(userAccountRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.APPROVED)).thenReturn(1L);

        assertThatThrownBy(() -> service.reject(USER_ID)).isInstanceOf(LastAdminProtectedException.class);
        verify(userAccountRepository, never()).save(admin);
    }

    @Test
    void revokeRequiresAnApprovedAccount() {
        UserAccount pending = pendingAccount();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.revoke(USER_ID)).isInstanceOf(UserNotApprovedException.class);
    }

    @Test
    void revokeClearsRoleAndGrants() {
        UserAccount guest = approvedGuest();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        when(userAccountRepository.save(guest)).thenReturn(guest);

        UserAccount revoked = service.revoke(USER_ID);

        assertSoftly(softly -> {
            softly.assertThat(revoked.getStatus()).isEqualTo(UserStatus.REVOKED);
            softly.assertThat(revoked.getRole()).isNull();
            softly.assertThat(revoked.getAppGrants()).isEmpty();
        });
    }

    @Test
    void grantAppAddsTheGrantToAnApprovedAccount() {
        UserAccount guest = approvedGuest();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        when(userAccountRepository.save(guest)).thenReturn(guest);

        service.grantApp(USER_ID, ManagedApp.GAME_CATALOG);

        assertThat(guest.getAppGrants()).contains(ManagedApp.GAME_CATALOG);
    }

    @Test
    void grantAppRefusesANonGrantableApp() {
        assertThatThrownBy(() -> service.grantApp(USER_ID, ManagedApp.FNA))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void grantAppRefusesAPendingAccount() {
        UserAccount pending = pendingAccount();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.grantApp(USER_ID, ManagedApp.GAME_CATALOG))
                .isInstanceOf(UserNotApprovedException.class);
    }

    @Test
    void revokeAppRemovesTheGrant() {
        UserAccount guest = approvedGuest();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        when(userAccountRepository.save(guest)).thenReturn(guest);

        service.revokeApp(USER_ID, ManagedApp.GAME_CATALOG);

        assertThat(guest.getAppGrants()).isEmpty();
    }

    @Test
    void changeOwnPasswordRequiresTheCurrentPassword() {
        UserAccount guest = approvedGuest();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        when(passwordEncoder.matches("wrong", ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword(USER_ID, "wrong", "another password 12"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void changeOwnPasswordStoresTheNewHash() {
        UserAccount guest = approvedGuest();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(passwordEncoder.encode("another password 12")).thenReturn("{bcrypt}$2a$10$new");
        when(userAccountRepository.save(guest)).thenReturn(guest);

        service.changeOwnPassword(USER_ID, PASSWORD, "another password 12");

        assertThat(guest.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$new");
        // A pending reset request is superseded by the self-service change (spec 019 D15).
        org.mockito.Mockito.verify(passwordResetRequestRepository)
                .deleteByUserAccountIdAndStatus(USER_ID,
                        dev.jordy.jordylab.identity.domain.ResetRequestStatus.PENDING);
    }

    @Test
    void changeOwnDisplayNameRejectsBlankNames() {
        UserAccount guest = approvedGuest();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> service.changeOwnDisplayName(USER_ID, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static UserAccount pendingAccount() {
        return UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.PENDING)
                .build();
    }

    private static UserAccount approvedGuest() {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.APPROVED)
                .role(UserRole.GUEST)
                .appGrants(java.util.Set.of(ManagedApp.GAME_CATALOG))
                .build();
        account.applyPassword(ENCODED);

        return account;
    }

    private static UserAccount approvedAdmin() {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .status(UserStatus.APPROVED)
                .role(UserRole.ADMIN)
                .build();
        account.applyPassword(ENCODED);

        return account;
    }
}
