package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The connector's per-call account check (spec 021 FR-004): only an approved admin is returned, so a demoted or revoked
 * account stops working on its next tool call.
 */
class UserAccountLookupTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserAccountLookup lookup = new UserAccountLookup(userAccountRepository);

    @Test
    void returnsAnApprovedAdmin() {
        UserAccount admin = account(UserRole.ADMIN);
        when(userAccountRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThat(lookup.findApprovedAdmin(admin.getId())).isPresent();
    }

    @Test
    void refusesAGuestEvenWhenApproved() {
        UserAccount guest = account(UserRole.GUEST);
        when(userAccountRepository.findById(guest.getId())).thenReturn(Optional.of(guest));

        assertThat(lookup.findApprovedAdmin(guest.getId())).isEmpty();
    }

    @Test
    void refusesARevokedAdmin() {
        UserAccount revoked = account(UserRole.ADMIN);
        revoked.revoke();
        when(userAccountRepository.findById(revoked.getId())).thenReturn(Optional.of(revoked));

        assertThat(lookup.findApprovedAdmin(revoked.getId())).isEmpty();
    }

    @Test
    void refusesAnAccountThatNoLongerExists() {
        UUID accountId = UUID.randomUUID();
        when(userAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThat(lookup.findApprovedAdmin(accountId)).isEmpty();
    }

    private static UserAccount account(UserRole role) {
        return UserAccount.builder()
                .username("mcp.account")
                .status(UserStatus.APPROVED)
                .role(role)
                .build();
    }
}
