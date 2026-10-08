package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.service.libbot.FactMatcher.GameFacts;
import dev.jordy.jordylab.gamecatalog.service.libbot.FactMatcher.Verdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactMatcherTest {

    private static final Constraints SIX_PLAYERS = new Constraints(6, true, false, false, null, null, null, null, null,
            null, null, null, false, null);

    private static GameFacts facts(Integer maxPlayers, Boolean local) {
        return new GameFacts(maxPlayers, local, null, null, null, null);
    }

    @Test
    void aKnownBigEnoughGroupIsConfirmed() {
        assertThat(FactMatcher.match(facts(8, true), SIX_PLAYERS)).isEqualTo(Verdict.CONFIRMED);
    }

    @Test
    void aKnownTooSmallGroupIsExcluded() {
        assertThat(FactMatcher.match(facts(4, true), SIX_PLAYERS)).isEqualTo(Verdict.EXCLUDED);
    }

    @Test
    void aGameKnownNotToBeLocalIsExcluded() {
        assertThat(FactMatcher.match(facts(null, false), SIX_PLAYERS)).isEqualTo(Verdict.EXCLUDED);
    }

    @Test
    void localSupportWithoutACountIsUnknown() {
        assertThat(FactMatcher.match(facts(null, true), SIX_PLAYERS)).isEqualTo(Verdict.UNKNOWN);
    }

    @Test
    void nothingKnownIsUnknownNotExcluded() {
        assertThat(FactMatcher.match(facts(null, null), SIX_PLAYERS)).isEqualTo(Verdict.UNKNOWN);
    }

    @Test
    void anExcludedFactBeatsAnUnknownOne() {
        Constraints playersAndOnline = new Constraints(6, true, false, true, null, null, null, null, null, null, null,
                null, false, null);

        assertThat(FactMatcher.match(new GameFacts(2, true, null, null, null, null), playersAndOnline))
                .isEqualTo(Verdict.EXCLUDED);
    }

    @Test
    void genresMatchBySubstringIgnoringCaseAndMissingGenresAreUnknown() {
        Constraints racing = new Constraints(null, false, false, false, null, null, null, java.util.List.of("racing"),
                null, null, null, null, false, null);

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(FactMatcher.match(new GameFacts(null, null, null, null, "Arcade, Racing", null), racing))
                    .isEqualTo(Verdict.CONFIRMED);
            softly.assertThat(FactMatcher.match(new GameFacts(null, null, null, null, "Puzzle", null), racing))
                    .isEqualTo(Verdict.EXCLUDED);
            softly.assertThat(FactMatcher.match(new GameFacts(null, null, null, null, null, null), racing))
                    .isEqualTo(Verdict.UNKNOWN);
        });
    }

    @Test
    void yearsAreInclusiveAndMissingYearsAreUnknown() {
        Constraints nineties = new Constraints(null, false, false, false, null, null, null, null, 1990, 1999, null, null,
                false, null);

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(FactMatcher.match(new GameFacts(null, null, null, null, null, 1999), nineties))
                    .isEqualTo(Verdict.CONFIRMED);
            softly.assertThat(FactMatcher.match(new GameFacts(null, null, null, null, null, 2000), nineties))
                    .isEqualTo(Verdict.EXCLUDED);
            softly.assertThat(FactMatcher.match(new GameFacts(null, null, null, null, null, null), nineties))
                    .isEqualTo(Verdict.UNKNOWN);
        });
    }

    @Test
    void noRequirementsConfirmEverything() {
        assertThat(FactMatcher.match(facts(null, null), Constraints.none())).isEqualTo(Verdict.CONFIRMED);
    }
}
