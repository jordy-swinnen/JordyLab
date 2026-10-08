package dev.jordy.jordylab.gamecatalog.domain;

/** Where a game's description came from: written by a model, or Steam's own store description (spec 013 FR-059). */
public enum DescriptionSource {
    AI,
    STEAM
}
