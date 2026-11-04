package dev.jordy.jordylab.identity.service;

import java.time.Instant;

/** Too many failed sign-ins; the account is locked until the given instant (spec 019 D9). */
public class AccountLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("Account locked until " + lockedUntil);
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
