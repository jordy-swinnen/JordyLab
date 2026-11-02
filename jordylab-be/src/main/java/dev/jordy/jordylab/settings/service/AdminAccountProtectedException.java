package dev.jordy.jordylab.settings.service;

import java.util.UUID;

/**
 * Thrown when an action is refused because its target is an admin account: deleting an admin or setting a personal
 * chat limit on one. The admin is never limited and can never be removed (spec 014 FR-013, FR-014).
 */
public class AdminAccountProtectedException extends RuntimeException {

    public AdminAccountProtectedException(UUID userId) {
        super("The admin account is protected: " + userId);
    }
}
