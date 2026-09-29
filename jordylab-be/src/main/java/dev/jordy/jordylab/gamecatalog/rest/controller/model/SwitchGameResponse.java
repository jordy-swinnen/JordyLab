package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.UUID;

/**
 * API response after adding a Switch game.
 */
public record SwitchGameResponse(UUID gameId, String title, String platform, String format) {
}
