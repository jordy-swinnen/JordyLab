package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** How the emulated copies of a game stand: one overall state and the counts behind it. Null for a game with no emulated copy. */
public record RomSummaryResponse(State state, int validated, int broken, int unknown, int total) {

    public enum State {
        UNKNOWN,
        VALIDATED,
        BROKEN,
        MIXED
    }
}
