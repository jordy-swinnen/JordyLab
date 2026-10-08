package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.Locale;

/**
 * Decides, for one game, whether it satisfies the fact requirements of a question (spec 013 FR-004). Three answers, not
 * two: {@code CONFIRMED} (every required fact is known and satisfied), {@code UNKNOWN} (nothing fails, but at least one
 * required fact is not known yet) and {@code EXCLUDED} (a known fact fails). Unknown games are reported separately and
 * never presented as matches.
 */
public final class FactMatcher {

    public enum Verdict {
        CONFIRMED,
        UNKNOWN,
        EXCLUDED
    }

    /** The facts of a game that requirements are checked against. */
    public record GameFacts(Integer maxLocalPlayers, Boolean localMultiplayer, Boolean singlePlayer,
            Boolean onlineMultiplayer, String genres, Integer releaseYear) {
    }

    private FactMatcher() {
    }

    public static Verdict match(GameFacts facts, Constraints constraints) {
        Verdict overall = Verdict.CONFIRMED;
        Verdict[] verdicts = {
                players(facts, constraints),
                flag(facts.localMultiplayer(), constraints.requireLocalMultiplayer()),
                flag(facts.singlePlayer(), constraints.requireSinglePlayer()),
                flag(facts.onlineMultiplayer(), constraints.requireOnline()),
                genres(facts.genres(), constraints),
                years(facts.releaseYear(), constraints)
        };
        for (Verdict verdict : verdicts) {
            if (verdict == Verdict.EXCLUDED) {
                return Verdict.EXCLUDED;
            }
            if (verdict == Verdict.UNKNOWN) {
                overall = Verdict.UNKNOWN;
            }
        }

        return overall;
    }

    private static Verdict players(GameFacts facts, Constraints constraints) {
        Integer required = constraints.minLocalPlayers();
        if (required == null) {
            return Verdict.CONFIRMED;
        }
        if (Boolean.FALSE.equals(facts.localMultiplayer())) {
            return Verdict.EXCLUDED;
        }
        if (facts.maxLocalPlayers() != null) {
            return facts.maxLocalPlayers() >= required ? Verdict.CONFIRMED : Verdict.EXCLUDED;
        }

        return Verdict.UNKNOWN;
    }

    private static Verdict flag(Boolean actual, boolean required) {
        if (!required) {
            return Verdict.CONFIRMED;
        }
        if (actual == null) {
            return Verdict.UNKNOWN;
        }

        return actual ? Verdict.CONFIRMED : Verdict.EXCLUDED;
    }

    private static Verdict genres(String actualGenres, Constraints constraints) {
        if (constraints.genres().isEmpty()) {
            return Verdict.CONFIRMED;
        }
        if (actualGenres == null || actualGenres.isBlank()) {
            return Verdict.UNKNOWN;
        }
        String haystack = actualGenres.toLowerCase(Locale.ROOT);

        return constraints.genres().stream().anyMatch(genre -> haystack.contains(genre.toLowerCase(Locale.ROOT)))
                ? Verdict.CONFIRMED : Verdict.EXCLUDED;
    }

    private static Verdict years(Integer releaseYear, Constraints constraints) {
        if (constraints.releaseYearMin() == null && constraints.releaseYearMax() == null) {
            return Verdict.CONFIRMED;
        }
        if (releaseYear == null) {
            return Verdict.UNKNOWN;
        }
        boolean afterStart = constraints.releaseYearMin() == null || releaseYear >= constraints.releaseYearMin();
        boolean beforeEnd = constraints.releaseYearMax() == null || releaseYear <= constraints.releaseYearMax();

        return afterStart && beforeEnd ? Verdict.CONFIRMED : Verdict.EXCLUDED;
    }
}
