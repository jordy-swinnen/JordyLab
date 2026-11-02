package dev.jordy.jordylab.settings.rest.controller.model;

import dev.jordy.jordylab.settings.service.GuestChatLimitService;

/** The limit in force for one person, with its source; {@code null} for the admin, who is never limited. */
public record PersonChatLimit(int limit, String source) {

    public static PersonChatLimit from(GuestChatLimitService.EffectiveGuestChatLimit effective) {
        return new PersonChatLimit(effective.limit(), effective.source().name());
    }
}
