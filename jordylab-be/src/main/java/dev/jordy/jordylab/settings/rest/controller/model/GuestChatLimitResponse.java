package dev.jordy.jordylab.settings.rest.controller.model;

import dev.jordy.jordylab.settings.service.GuestChatLimitService;

import java.time.Instant;

public record GuestChatLimitResponse(int dailyLimit, String source, Instant updatedAt, String updatedBy) {

    public static GuestChatLimitResponse from(GuestChatLimitService.DefaultLimitView view) {
        return new GuestChatLimitResponse(view.dailyLimit(), view.source().name(), view.updatedAt(), view.updatedBy());
    }
}
