package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.domain.PasswordResetRequest;
import dev.jordy.jordylab.identity.domain.ResetRequestStatus;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.PasswordResetRequestRepository;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID REQUEST_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final String NEW_PASSWORD = "a much better password";

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final PasswordResetRequestRepository passwordResetRequestRepository =
            mock(PasswordResetRequestRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final PasswordResetService service = new PasswordResetService(userAccountRepository,
            passwordResetRequestRepository, passwordEncoder, properties(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void requestStoresOnlyTheHashAndReplacesThePendingOne() {
        UserAccount account = approvedAccount();
        when(userAccountRepository.findByUsername("guest-account")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("{bcrypt}$2a$10$new");
        ArgumentCaptor<PasswordResetRequest> saved = ArgumentCaptor.forClass(PasswordResetRequest.class);
        when(passwordResetRequestRepository.save(saved.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.request(" Guest-Account ", NEW_PASSWORD);

        assertSoftly(softly -> {
            softly.assertThat(saved.getValue().getUserAccountId()).isEqualTo(USER_ID);
            softly.assertThat(saved.getValue().getRequestedPasswordHash()).isEqualTo("{bcrypt}$2a$10$new");
            softly.assertThat(saved.getValue().getRequestedPasswordHash()).doesNotContain(NEW_PASSWORD);
            softly.assertThat(saved.getValue().isPending()).isTrue();
        });
        verify(passwordResetRequestRepository)
                .deleteByUserAccountIdAndStatus(USER_ID, ResetRequestStatus.PENDING);
    }

    @Test
    void requestForAnUnknownUsernameCreatesNothing() {
        when(userAccountRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("{bcrypt}$2a$10$hash");

        service.request("nobody", NEW_PASSWORD);

        verify(passwordResetRequestRepository, never()).save(any());
    }

    @Test
    void requestStillHashesForUnknownUsernames() {
        when(userAccountRepository.findByUsername(any())).thenReturn(Optional.empty());

        service.request("nobody", NEW_PASSWORD);

        verify(passwordEncoder).encode(NEW_PASSWORD);
    }

    @Test
    void requestRejectsAWeakPasswordBeforeLookingTheAccountUp() {
        assertThatThrownBy(() -> service.request("guest-account", "short"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("at least 12 characters");
        verify(userAccountRepository, never()).findByUsername(any());
        verify(passwordResetRequestRepository, never()).save(any());
    }

    @Test
    void requestRejectsTheUsernameAsPassword() {
        assertThatThrownBy(() -> service.request("guestaccount", "guestaccount"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void approveAppliesTheRequestedHashAndClosesTheRequest() {
        UserAccount account = approvedAccount();
        PasswordResetRequest request = pendingRequest();
        when(passwordResetRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(account)).thenReturn(account);
        when(passwordResetRequestRepository.save(request)).thenReturn(request);

        service.approve(ADMIN_ID, USER_ID, REQUEST_ID);

        assertSoftly(softly -> {
            softly.assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$requested");
            softly.assertThat(request.getStatus()).isEqualTo(ResetRequestStatus.APPROVED);
            softly.assertThat(request.getDecidedBy()).isEqualTo(ADMIN_ID);
            softly.assertThat(request.getDecidedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void approveRefusesADecidedRequest() {
        PasswordResetRequest request = pendingRequest();
        request.reject(ADMIN_ID, NOW);
        when(passwordResetRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(ADMIN_ID, USER_ID, REQUEST_ID))
                .isInstanceOf(ResetRequestNotPendingException.class);
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void approveRefusesARequestOfAnotherAccount() {
        when(passwordResetRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.approve(ADMIN_ID,
                UUID.fromString("44444444-4444-4444-8444-444444444444"), REQUEST_ID))
                .isInstanceOf(ResetRequestNotFoundException.class);
    }

    @Test
    void rejectClosesTheRequestAndKeepsTheOldPassword() {
        UserAccount account = approvedAccount();
        PasswordResetRequest request = pendingRequest();
        when(passwordResetRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(passwordResetRequestRepository.save(request)).thenReturn(request);

        service.reject(ADMIN_ID, USER_ID, REQUEST_ID);

        assertThat(request.getStatus()).isEqualTo(ResetRequestStatus.REJECTED);
        assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$old");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void aDirectResetReturnsTheTemporaryPasswordAndSupersedesThePendingRequest() {
        UserAccount account = approvedAccount();
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(userAccountRepository.save(account)).thenReturn(account);
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}$2a$10$temporary");

        String temporaryPassword = service.resetDirectly(USER_ID);

        assertThat(temporaryPassword).hasSize(20);
        assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$temporary");
        verify(passwordEncoder).encode(temporaryPassword);
        verify(passwordResetRequestRepository)
                .deleteByUserAccountIdAndStatus(USER_ID, ResetRequestStatus.PENDING);
    }

    @Test
    void aDirectResetRefusesAnUnknownAccount() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetDirectly(USER_ID)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void pendingByUserIsKeyedByAccount() {
        when(passwordResetRequestRepository.findByStatus(ResetRequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest()));

        Map<UUID, PasswordResetRequest> pending = service.pendingByUser();

        assertThat(pending).containsKey(USER_ID);
    }

    private static PasswordResetRequest pendingRequest() {
        return PasswordResetRequest.builder()
                .id(REQUEST_ID)
                .userAccountId(USER_ID)
                .requestedPasswordHash("{bcrypt}$2a$10$requested")
                .build();
    }

    private static UserAccount approvedAccount() {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username("guest-account")
                .status(UserStatus.APPROVED)
                .role(UserRole.GUEST)
                .build();
        account.applyPassword("{bcrypt}$2a$10$old");

        return account;
    }

    private static IdentityProperties properties() {
        return new IdentityProperties(
                new IdentityProperties.Password(12),
                new IdentityProperties.Lockout(5, 15),
                new IdentityProperties.Bootstrap("", ""),
                new IdentityProperties.WebAuthn("localhost", "JordyLab", List.of("http://localhost:4200")));
    }
}
