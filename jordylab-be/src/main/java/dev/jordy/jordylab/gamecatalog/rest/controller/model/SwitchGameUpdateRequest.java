package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;

/**
 * API request to update a Switch installation. A new {@code igdbGameId} relinks the game and
 * refreshes deterministic metadata/artwork/multiplayer without regenerating the AI description.
 */
public record SwitchGameUpdateRequest(InstallationFormat format, Long igdbGameId) {
}
