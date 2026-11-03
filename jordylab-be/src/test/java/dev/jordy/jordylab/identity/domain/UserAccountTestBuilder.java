package dev.jordy.jordylab.identity.domain;

import dev.jordy.jordylab.identity.ManagedApp;
import lombok.experimental.UtilityClass;

import java.util.Set;
import java.util.UUID;

import static dev.jordy.jordylab.identity.ManagedApp.GAME_CATALOG;

@UtilityClass
class UserAccountTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");
    public static final String DEFAULT_USERNAME = "jordy";
    public static final String DEFAULT_DISPLAY_NAME = "Jordy Swinnen";
    public static final String DEFAULT_PASSWORD_HASH = "{bcrypt}$2a$10$abcdefghijklmnopqrstuv";
    public static final UserRole DEFAULT_ROLE = UserRole.GUEST;
    public static final UserStatus DEFAULT_STATUS = UserStatus.APPROVED;
    public static final Set<ManagedApp> DEFAULT_GRANTS = Set.of(GAME_CATALOG);

    public static UserAccount aDefaultUserAccount() {
        return aUserAccount().build();
    }

    public static UserAccount.UserAccountBuilder aUserAccount() {
        return UserAccount.builder()
                .id(DEFAULT_ID)
                .username(DEFAULT_USERNAME)
                .displayName(DEFAULT_DISPLAY_NAME)
                .passwordHash(DEFAULT_PASSWORD_HASH)
                .role(DEFAULT_ROLE)
                .status(DEFAULT_STATUS)
                .appGrants(DEFAULT_GRANTS);
    }

    public static UserAccount aPendingUserAccount() {
        return aUserAccount()
                .role(null)
                .status(UserStatus.PENDING)
                .appGrants(null)
                .build();
    }
}
