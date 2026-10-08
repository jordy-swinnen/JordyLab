package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** Public vote totals for one game; never says who voted (spec 013 FR-043 to FR-046). */
public record VoteTotalsResponse(long wantToPlay, long playedLiked, long playedDisliked) {

    public static VoteTotalsResponse none() {
        return new VoteTotalsResponse(0, 0, 0);
    }
}
