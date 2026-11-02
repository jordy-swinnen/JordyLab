package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;
import java.util.UUID;

/**
 * The games behind one health count, for the "show exceptions" list; capped, with the real total alongside. {@code reason}
 * is only set for player-count lists (spec 014).
 */
public record HealthExceptionsResponse(String kind, long total, List<HealthException> games) {

    public record HealthException(UUID id, String title, PlayerCountReason reason) {
    }
}
