package dev.jordy.jordylab.settings.rest.controller.model;

/** Whole number of 0 or more; 0 blocks that person's chat (spec 014 FR-011). */
public record SavePersonalChatLimitRequest(int dailyLimit) {
}
