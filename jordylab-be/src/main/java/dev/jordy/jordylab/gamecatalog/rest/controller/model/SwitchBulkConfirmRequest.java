package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The ticked lines of a reviewed Switch bulk add (spec 009 US3 AS2). A line with an {@code igdbGameId} is added
 * from IGDB, otherwise by its {@code title}.
 */
public record SwitchBulkConfirmRequest(@NotEmpty @Size(max = 100) List<@Valid Item> items) {

    public record Item(@NotNull String line, Long igdbGameId, String title, @NotNull InstallationFormat format) {
    }
}
