package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.UUID;

/** A game on a console, for the console's games list. */
public record ConsoleGameListItem(UUID gameId, String title, Integer releaseYear, String coverUrl, String coverEndpoint) {
}
