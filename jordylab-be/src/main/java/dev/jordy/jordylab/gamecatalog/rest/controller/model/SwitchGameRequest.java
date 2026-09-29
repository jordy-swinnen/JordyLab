package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import jakarta.validation.constraints.NotNull;

/**
 * API request to add a Switch game from IGDB search or manually.
 */
public record SwitchGameRequest(Long igdbGameId, String title,
        @NotNull InstallationFormat format) {
}
