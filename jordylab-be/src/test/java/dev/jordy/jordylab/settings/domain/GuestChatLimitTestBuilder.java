package dev.jordy.jordylab.settings.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class GuestChatLimitTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("8f2c4d1e-6a3b-4c7d-9e0f-1a2b3c4d5e6f");
    public static final String DEFAULT_USER_SUBJECT = "keycloak-subject-guest-1";
    public static final int DEFAULT_DAILY_LIMIT = 20;
    public static final String DEFAULT_UPDATED_BY_SUBJECT = "keycloak-subject-admin";

    public static GuestChatLimit aDefaultGuestChatLimit() {
        return aGuestChatLimit().build();
    }

    /** The saved default row: no {@code userSubject}. */
    public static GuestChatLimit aSavedDefault() {
        return aGuestChatLimit().userSubject(null).build();
    }

    public static GuestChatLimit.GuestChatLimitBuilder aGuestChatLimit() {
        return GuestChatLimit.builder()
                .id(DEFAULT_ID)
                .userSubject(DEFAULT_USER_SUBJECT)
                .dailyLimit(DEFAULT_DAILY_LIMIT)
                .updatedBySubject(DEFAULT_UPDATED_BY_SUBJECT);
    }
}
