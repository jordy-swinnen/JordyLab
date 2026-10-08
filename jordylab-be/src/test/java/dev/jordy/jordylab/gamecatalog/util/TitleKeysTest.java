package dev.jordy.jordylab.gamecatalog.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TitleKeysTest {

    @ParameterizedTest(name = "\"{0}\" and \"{1}\" are the same game")
    @CsvSource(delimiter = '|', value = {
            "Baldur's Gate - Dark Alliance|Baldurs Gate: Dark Alliance",
            "Pokémon Emerald|Pokemon Emerald",
            "Super Mario World (USA)|super mario world",
            "Mega Man X [!]|Mega Man X",
            "Super Mario Bros. (Europe) (Rev A)|Super Mario Bros",
            "The Legend of Zelda: Ocarina of Time|Legend of Zelda Ocarina of Time",
            "Ratchet & Clank|Ratchet and Clank",
            "Witcher 3: Game of the Year Edition|The Witcher 3",
            "Hades   II|HADES II",
            "Sid Meier’s Civilization VI|Sid Meiers Civilization VI"
    })
    void sameTitleAfterIgnoringCaseAccentsPunctuationTagsAndEditions(String first, String second) {
        assertThat(TitleKeys.normalise(first)).isEqualTo(TitleKeys.normalise(second));
    }

    @ParameterizedTest(name = "\"{0}\" and \"{1}\" stay different games")
    @CsvSource(delimiter = '|', value = {
            "Resident Evil 4|Resident Evil 2",
            "Hades|Hades II",
            "Final Fantasy VII|Final Fantasy VII Remake",
            "Mario Kart 8 Deluxe|Mario Kart 64"
    })
    void differentTitlesStayDifferent(String first, String second) {
        assertThat(TitleKeys.normalise(first)).isNotEqualTo(TitleKeys.normalise(second));
    }

    @Test
    void producesTheExpectedKeyForARegionTaggedRomName() {
        assertThat(TitleKeys.normalise("Sonic The Hedgehog 2 (USA, Europe) [!]")).isEqualTo("sonic the hedgehog 2");
    }

    @Test
    void dropsOnlyALeadingThe() {
        assertThat(TitleKeys.normalise("Into the Breach")).isEqualTo("into the breach");
    }

    @Test
    void nullBlankAndSymbolOnlyTitlesBecomeAnEmptyKey() {
        assertThat(TitleKeys.normalise(null)).isEmpty();
        assertThat(TitleKeys.normalise("   ")).isEmpty();
        assertThat(TitleKeys.normalise("(USA)")).isEmpty();
    }
}
