package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConsoleBulkConfirmRequest(@NotNull List<Item> items) {

    /** One ticked line: added by IGDB id when there is a match, by title otherwise. */
    public record Item(String line, Long igdbGameId, String title) {
    }
}
