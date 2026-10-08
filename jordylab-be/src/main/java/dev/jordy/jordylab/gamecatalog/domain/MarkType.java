package dev.jordy.jordylab.gamecatalog.domain;

/** One user's single opinion about a game (spec 013 US10); the three options exclude each other. */
public enum MarkType {
    WANT_TO_PLAY,
    PLAYED_LIKED,
    PLAYED_DISLIKED
}
