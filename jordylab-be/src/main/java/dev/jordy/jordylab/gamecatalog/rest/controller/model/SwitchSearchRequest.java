package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * API request to search IGDB for a Nintendo Switch title.
 */
public record SwitchSearchRequest(@NotBlank String query) {
}
