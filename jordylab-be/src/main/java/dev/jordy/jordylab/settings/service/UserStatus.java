package dev.jordy.jordylab.settings.service;

/** Derived status (data-model.md D6) — never stored, always computed from Keycloak's own state. */
public enum UserStatus {
    PENDING,
    APPROVED,
    REJECTED
}
