package dev.jordy.jordylab.settings.service;

import java.util.UUID;

/**
 * Thrown when the admin tries to change their own access from the panel: delete, revoke, grant, limit or session
 * changes (spec 014 FR-020, FR-024). The panel never offers these actions, and the server refuses them as well.
 */
public class CannotChangeOwnAccountException extends RuntimeException {

    public CannotChangeOwnAccountException(UUID userId) {
        super("The admin cannot change their own account from the panel: " + userId);
    }
}
