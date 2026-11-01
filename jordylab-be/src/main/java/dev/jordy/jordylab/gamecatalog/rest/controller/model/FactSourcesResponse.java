package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;

/**
 * Where the Spec sheet's facts came from: {@code facts} is STEAM or AI (null when none), {@code multiplayer} STEAM or
 * IGDB for the multiplayer flags, and {@code playerCount} IGDB or THEGAMESDB for the player count (null while unknown).
 */
public record FactSourcesResponse(String facts, MultiplayerSource multiplayer, PlayerCountSource playerCount) {
}
