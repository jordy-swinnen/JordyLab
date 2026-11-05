package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only lookups other modules may use (spec 019 FR-009): the identity module's internal repository stays internal,
 * so {@code settings} asks through this narrow public API (the personal chat limit needs to know whether a person
 * exists and whether they are an admin).
 */
@Component
@RequiredArgsConstructor
public class UserAccountLookup {

    private final UserAccountRepository userAccountRepository;

    public Optional<AccountRef> find(UUID userId) {
        return userAccountRepository.findById(userId).map(AccountRef::from);
    }

    /**
     * The account only when it is approved and still an admin (spec 021 FR-004): the connector re-checks this on every tool
     * call, because a JWT cannot be recalled. Status and role stay inside the identity module.
     */
    public Optional<AccountRef> findApprovedAdmin(UUID userId) {
        return userAccountRepository.findById(userId)
                .filter(account -> account.getStatus() == UserStatus.APPROVED)
                .filter(account -> account.getRole() == UserRole.ADMIN)
                .map(AccountRef::from);
    }

    /** The little another module may know about an account. */
    public record AccountRef(UUID id, String username, String displayName, String role) {

        static AccountRef from(UserAccount account) {
            return new AccountRef(account.getId(), account.getUsername(), account.getDisplayName(),
                    account.roleKey().orElse(null));
        }
    }
}
