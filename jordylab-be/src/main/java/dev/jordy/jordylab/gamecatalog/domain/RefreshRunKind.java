package dev.jordy.jordylab.gamecatalog.domain;

/** {@code DATA} re-fetches facts and artwork for every game; {@code AI} regenerates the AI-written descriptions (spec 013 US11). */
public enum RefreshRunKind {
    DATA,
    AI
}
