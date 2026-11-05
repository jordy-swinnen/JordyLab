package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.identity.UserAccountLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The account check the connector makes on every call and on every authorization request (spec 021 FR-004, research R3).
 * A token cannot be recalled, so its claims are never enough on their own: the account must still exist, be approved and
 * still be an admin. The check reads the database each time and caches nothing, so a removed role or a disabled account
 * stops the very next call.
 */
@Component
@RequiredArgsConstructor
public class McpAccountStatusValidator {

    private final UserAccountLookup userAccountLookup;

    /** The token's subject is the account UUID (contracts/oauth-authorization-server.md). */
    public boolean isActiveAdmin(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }
        try {
            return isActiveAdmin(UUID.fromString(subject));
        } catch (IllegalArgumentException notAnAccountId) {
            return false;
        }
    }

    public boolean isActiveAdmin(UUID accountId) {
        return accountId != null && userAccountLookup.findApprovedAdmin(accountId).isPresent();
    }
}
