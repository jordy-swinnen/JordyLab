package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;

/** Where the Spec sheet's facts came from: {@code facts} is STEAM or AI (null when none), {@code multiplayer} STEAM or IGDB. */
public record FactSourcesResponse(String facts, MultiplayerSource multiplayer) {
}
