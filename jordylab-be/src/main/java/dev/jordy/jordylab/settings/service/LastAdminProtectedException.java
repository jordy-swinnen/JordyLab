package dev.jordy.jordylab.settings.service;

/** FR-007: there must always be at least one enabled admin. */
public class LastAdminProtectedException extends RuntimeException {

    public LastAdminProtectedException() {
        super("The last enabled admin cannot be rejected or revoked");
    }
}
