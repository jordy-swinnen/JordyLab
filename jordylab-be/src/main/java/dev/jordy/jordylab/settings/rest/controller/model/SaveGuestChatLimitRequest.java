package dev.jordy.jordylab.settings.rest.controller.model;

/** Whole number of 0 or more; 0 blocks chat for every guest without a personal limit (spec 014 FR-011). */
public record SaveGuestChatLimitRequest(int dailyLimit) {
}
