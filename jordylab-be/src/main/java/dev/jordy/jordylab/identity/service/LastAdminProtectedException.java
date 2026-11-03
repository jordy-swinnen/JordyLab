package dev.jordy.jordylab.identity.service;

/** The last approved admin cannot be rejected, revoked or deleted (spec 014 FR-007 parity). */
public class LastAdminProtectedException extends RuntimeException {
}
