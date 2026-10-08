package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.service.DescriptionQualityValidator.Verdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class DescriptionQualityValidatorTest {

    private static final String GOOD = "Hades is an action roguelike in which the player fights out of the underworld as "
            + "Zagreus, the son of the god of the dead. Each escape attempt ends in defeat and a return to the house, "
            + "where new weapons, upgrades and conversations carry over to the next run. Combat is fast and built "
            + "around dashing, attacking and choosing boons granted by the gods of Olympus.";

    private final DescriptionQualityValidator validator = new DescriptionQualityValidator();

    private Verdict check(String text) {
        return validator.check(text, 2020);
    }

    @Test
    void aStoreBlurbOfTwoToFourSentencesAndAboutAHundredWordsIsAccepted() {
        assertThat(check(GOOD).accepted()).isTrue();
    }

    @Test
    void aTextWithoutAKnownReleaseYearIsJudgedOnTheRest() {
        assertThat(validator.check(GOOD, null).accepted()).isTrue();
    }

    @Test
    void emptyOrOneLinerTextsAreRejected() {
        assertSoftly(softly -> {
            softly.assertThat(check(null).accepted()).isFalse();
            softly.assertThat(check("   ").accepted()).isFalse();
            softly.assertThat(check("Hades is a roguelike.").reason()).contains("two to four sentences");
        });
    }

    @Test
    void tooManySentencesAreRejected() {
        String tooMany = GOOD + " The story unfolds slowly. Characters return often.";

        assertThat(check(tooMany).reason()).contains("two to four sentences");
    }

    @Test
    void textsThatAreTooShortOrTooLongInWordsAreRejected() {
        String shortText = "Hades is a roguelike. The player fights through the underworld.";
        String longText = GOOD + " " + "The game keeps adding systems, rooms, characters and weapons ".repeat(10).trim() + ".";

        assertSoftly(softly -> {
            softly.assertThat(check(shortText).reason()).contains("40 to 110 words");
            softly.assertThat(check(longText).accepted()).isFalse();
        });
    }

    @Test
    void firstPersonAndAsAnAiAreRejected() {
        String firstPerson = GOOD.replace("Combat is fast", "I found the combat fast");
        String asAnAi = GOOD.replace("Hades is an action roguelike", "As an AI, I would say Hades is an action roguelike");

        assertSoftly(softly -> {
            softly.assertThat(check(firstPerson).reason()).contains("third person");
            softly.assertThat(check(asAnAi).reason()).contains("AI");
        });
    }

    @Test
    void marketingFillerListsAreRejected() {
        String filler = GOOD.replace("Combat is fast and built around dashing, attacking and choosing boons",
                "Combat is stunning, immersive and epic, an unforgettable masterpiece of choices");

        assertThat(check(filler).reason()).contains("marketing adjectives");
    }

    @Test
    void inventedScoresAreRejected() {
        String scored = GOOD.replace("Combat is fast", "It scored 9/10 and combat is fast");

        assertThat(check(scored).reason()).contains("scores");
    }

    @Test
    void aStatedReleaseYearThatContradictsTheKnownOneIsRejectedButAMatchingOneIsFine() {
        String wrongYear = GOOD.replace("Each escape", "Released in 2018, each escape");
        String rightYear = GOOD.replace("Each escape", "Released in 2020, each escape");

        assertSoftly(softly -> {
            softly.assertThat(check(wrongYear).reason()).contains("2020");
            softly.assertThat(check(rightYear).accepted()).isTrue();
        });
    }
}
