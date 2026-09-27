package dev.jordy.jordylab.gamecatalog.domain;

/**
 * Provenance of a game's structured multiplayer data. {@code UNKNOWN} means no source has
 * resolved it yet (or it is parked after repeated failures) — never an LLM guess.
 */
public enum MultiplayerSource {
    STEAM,
    IGDB,
    UNKNOWN
}
