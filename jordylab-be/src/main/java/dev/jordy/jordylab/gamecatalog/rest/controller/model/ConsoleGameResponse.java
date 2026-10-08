package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.UUID;

/** {@code linkedExisting} is true when the game was already in the catalog (Steam, an emulator, another console). */
public record ConsoleGameResponse(UUID gameId, String title, boolean linkedExisting) {
}
