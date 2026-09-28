package dev.jordy.jordylab.settings.domain;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.util.UUID;

@UtilityClass
class GuestChatUsageTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("2b6f1a3c-4d5e-4f60-8a1b-9c2d3e4f5a6b");
    public static final String DEFAULT_USER_SUBJECT = "keycloak-subject-guest-1";
    public static final LocalDate DEFAULT_USAGE_DATE = LocalDate.parse("2026-09-27");
    public static final int DEFAULT_MESSAGE_COUNT = 1;

    public static GuestChatUsage aDefaultGuestChatUsage() {
        return aGuestChatUsage().build();
    }

    public static GuestChatUsage.GuestChatUsageBuilder aGuestChatUsage() {
        return GuestChatUsage.builder()
                .id(DEFAULT_ID)
                .userSubject(DEFAULT_USER_SUBJECT)
                .usageDate(DEFAULT_USAGE_DATE)
                .messageCount(DEFAULT_MESSAGE_COUNT);
    }
}
