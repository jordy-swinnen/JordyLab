package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;

import java.util.List;

/**
 * The hard requirements derived from a question's facts (spec 013 FR-004, FR-005), applied to the library by the
 * retriever. Facts a game does not state are "unknown", not "failing": see {@link FactMatcher}.
 */
public record Constraints(
        Integer minLocalPlayers,
        boolean requireLocalMultiplayer,
        boolean requireSinglePlayer,
        boolean requireOnline,
        List<String> platforms,
        List<String> places,
        InstallFilter installStatus,
        List<String> genres,
        Integer releaseYearMin,
        Integer releaseYearMax,
        MarkType markFilter,
        MarkScope markScope,
        boolean likeMyLiked,
        String semanticQuery) {

    public Constraints {
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        places = places == null ? List.of() : List.copyOf(places);
        genres = genres == null ? List.of() : List.copyOf(genres);
        markScope = markScope == null ? MarkScope.MINE : markScope;
    }

    public static Constraints none() {
        return new Constraints(null, false, false, false, List.of(), List.of(), null, List.of(), null, null, null,
                MarkScope.MINE, false, null);
    }

    public Constraints withLikeMyLiked(boolean value) {
        return new Constraints(minLocalPlayers, requireLocalMultiplayer, requireSinglePlayer, requireOnline, platforms,
                places, installStatus, genres, releaseYearMin, releaseYearMax, markFilter, markScope, value,
                semanticQuery);
    }

    /** True when at least one fact about the games themselves (players, genres, years) is required. */
    public boolean hasFactRequirements() {
        return minLocalPlayers != null || requireLocalMultiplayer || requireSinglePlayer || requireOnline
                || !genres.isEmpty() || releaseYearMin != null || releaseYearMax != null;
    }
}
