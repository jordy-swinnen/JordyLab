package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LibBotAnswerValidatorTest {

    private static final Candidate OVERCOOKED = candidate("Overcooked! All You Can Eat");
    private static final Candidate PORTAL = candidate("Portal");
    private static final Candidate PORTAL_TWO = candidate("Portal 2");
    private static final Candidate CELESTE = candidate("Celeste");
    private static final Candidate POKEMON = candidate("Pokémon Légendes");

    private final LibBotAnswerValidator validator = new LibBotAnswerValidator(
            new LibBotProperties(10, 2, 3, 15, 5, 1000));

    private static Candidate candidate(String title) {
        return Candidate.builder().gameId(UUID.randomUUID()).title(title).platforms(List.of("Steam")).confirmed(true)
                .build();
    }

    private static LibBotAnswer answer(String text, Candidate... recommended) {
        return new LibBotAnswer(text, List.of(recommended).stream().map(Candidate::gameId).toList());
    }

    @Test
    void keepsRecommendedGamesNamedInTheText() {
        List<Candidate> references = validator.validReferences(
                answer("Try Overcooked! All You Can Eat tonight.", OVERCOOKED), List.of(OVERCOOKED, CELESTE));

        assertThat(references).containsExactly(OVERCOOKED);
    }

    @Test
    void dropsARecommendedGameTheTextNeverNames() {
        List<Candidate> references = validator.validReferences(
                answer("Try Overcooked! All You Can Eat tonight.", OVERCOOKED, CELESTE), List.of(OVERCOOKED, CELESTE));

        assertThat(references).containsExactly(OVERCOOKED);
    }

    @Test
    void dropsIdsThatAreNotCandidates() {
        Candidate stranger = candidate("Hades");

        List<Candidate> references = validator.validReferences(answer("Hades is great, so is Celeste.", stranger, CELESTE),
                List.of(CELESTE));

        assertThat(references).containsExactly(CELESTE);
    }

    @Test
    void fallsBackToTheCandidatesNamedInTheTextWhenNothingValidWasRecommended() {
        List<Candidate> references = validator.validReferences(answer("Celeste is a good pick."),
                List.of(CELESTE, OVERCOOKED));

        assertThat(references).containsExactly(CELESTE);
    }

    @Test
    void trimsToTheMaximumInOrderOfAppearance() {
        Candidate a = candidate("Alpha");
        Candidate b = candidate("Bravo");
        Candidate c = candidate("Charlie");
        Candidate d = candidate("Delta");

        List<Candidate> references = validator.validReferences(answer("Delta, then Bravo, Alpha and Charlie.", a, b, c, d),
                List.of(a, b, c, d));

        assertThat(references).containsExactly(d, b, a);
    }

    @Test
    void matchingIgnoresCaseAccentsAndPunctuation() {
        List<Candidate> references = validator.validReferences(
                answer("pokemon legendes is wonderful and OVERCOOKED all you can eat too", POKEMON, OVERCOOKED),
                List.of(POKEMON, OVERCOOKED));

        assertThat(references).containsExactlyInAnyOrder(POKEMON, OVERCOOKED);
    }

    @Test
    void aShorterTitleDoesNotMatchInsideALongerOneThatWasNamed() {
        List<Candidate> references = validator.validReferences(answer("Portal 2 is the one to play.", PORTAL, PORTAL_TWO),
                List.of(PORTAL, PORTAL_TWO));

        assertThat(references).containsExactly(PORTAL_TWO);
    }

    @Test
    void bothTitlesMatchWhenBothAreNamed() {
        List<Candidate> references = validator.validReferences(answer("Portal and Portal 2 both work."),
                List.of(PORTAL, PORTAL_TWO));

        assertThat(references).containsExactlyInAnyOrder(PORTAL, PORTAL_TWO);
    }

    @Test
    void partialWordsDoNotMatch() {
        Candidate cel = candidate("Cel");

        assertThat(validator.validReferences(answer("Celeste is nice."), List.of(cel))).isEmpty();
    }
}
