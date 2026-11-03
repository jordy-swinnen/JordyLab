package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.UserAccessRemoved;
import dev.jordy.jordylab.identity.UserDeleted;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDeletionServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserSessionService userSessionService = mock(UserSessionService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final UserDeletionService service =
            new UserDeletionService(userAccountRepository, userSessionService, eventPublisher);

    @Test
    void refusesTheCallersOwnAccount() {
        assertThatThrownBy(() -> service.delete(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(OwnAccountOperationException.class);
        verify(userAccountRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesAnyAdminAccount() {
        UserAccount admin = account(UserRole.ADMIN);
        when(userAccountRepository.findById(TARGET_ID)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.delete(ADMIN_ID, TARGET_ID))
                .isInstanceOf(AdminAccountProtectedException.class);
        verify(userAccountRepository, never()).delete(admin);
    }

    @Test
    void endsSessionsPublishesEventsAndDeletesANonAdminAccount() {
        UserAccount guest = account(UserRole.GUEST);
        when(userAccountRepository.findById(TARGET_ID)).thenReturn(Optional.of(guest));

        service.delete(ADMIN_ID, TARGET_ID);

        verify(userSessionService).endAllFor(guest);
        verify(eventPublisher).publishEvent(new UserAccessRemoved(TARGET_ID.toString()));
        verify(eventPublisher).publishEvent(new UserDeleted(TARGET_ID.toString()));
        verify(userAccountRepository).delete(guest);
    }

    @Test
    void refusesAnUnknownAccount() {
        when(userAccountRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ADMIN_ID, TARGET_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    private static UserAccount account(UserRole role) {
        UserAccount account = UserAccount.builder()
                .id(TARGET_ID)
                .username("guest-account")
                .status(UserStatus.APPROVED)
                .role(role)
                .build();
        account.applyPassword("{bcrypt}$2a$10$hash");

        return account;
    }
}
