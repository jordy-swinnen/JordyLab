package dev.jordy.jordylab.identity.domain;

import java.util.Locale;

/**
 * The two person roles (spec 019 FR-003/FR-008). The role grants {@code ROLE_admin} or {@code ROLE_guest}; per-app
 * access comes from the separate grants on {@link UserAccount}.
 */
public enum UserRole {

    ADMIN,

    GUEST;

    /** The role name as the API and authorities spell it ({@code admin}, {@code guest}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
