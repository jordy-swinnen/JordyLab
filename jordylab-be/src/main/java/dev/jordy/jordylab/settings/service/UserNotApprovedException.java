package dev.jordy.jordylab.settings.service;

import java.util.UUID;

/** Raised by revoke when the target does not currently hold {@code guest}. */
public class UserNotApprovedException extends RuntimeException {

    public UserNotApprovedException(UUID userId) {
        super("User is not approved: " + userId);
    }
}
