package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** How many people marked a game, by mark. Totals are public; voters never are (feature 013). */
public record MarkTotals(long wantToPlay, long playedLiked, long playedDisliked) {
}
