package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** How complete the visible library is (spec 013 FR-021). The percentages are of {@code totalGames}. */
public record LibraryHealthResponse(long totalGames, long gamesWithoutCover, long gamesWithoutDescription,
        long gamesPendingIndex) {
}
