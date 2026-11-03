package dev.jordy.jordylab.identity.domain;

/**
 * The stored lifecycle of an account (spec 019 D7). Replaces the status that was derived from Keycloak's enabled flag
 * and realm roles in spec 014: {@code PENDING} has no role yet, {@code APPROVED} carries a role, {@code REJECTED} and
 * {@code REVOKED} cannot sign in.
 */
public enum UserStatus {

    PENDING,

    APPROVED,

    REJECTED,

    REVOKED
}
