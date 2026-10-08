package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.service.libbot.AppliedConstraint.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class LibBotMessagesTest {

    private final LibBotMessages messages = new LibBotMessages();

    @Test
    void rendersTheAppliedChipsInEnglishAndDutch() {
        assertSoftly(softly -> {
            softly.assertThat(messages.applied(AppliedConstraint.of(Kind.MIN_LOCAL_PLAYERS, "6"), Language.EN))
                    .isEqualTo("6+ local players");
            softly.assertThat(messages.applied(AppliedConstraint.of(Kind.MIN_LOCAL_PLAYERS, "6"), Language.NL))
                    .isEqualTo("6+ lokale spelers");
            softly.assertThat(messages.applied(AppliedConstraint.of(Kind.INSTALLED), Language.NL))
                    .isEqualTo("geïnstalleerd");
            softly.assertThat(messages.applied(AppliedConstraint.of(Kind.PLACE, "Living room PC"), Language.EN))
                    .isEqualTo("Living room PC");
        });
    }

    @Test
    void theUnknownNoteHandlesSingularPluralAndTheGenericCase() {
        assertSoftly(softly -> {
            softly.assertThat(messages.unknownNote(31, 6, Language.EN))
                    .isEqualTo("For 31 more games the player count isn't known yet, so I can't say whether they fit 6 players.");
            softly.assertThat(messages.unknownNote(1, 6, Language.EN)).contains("1 more game ").contains("it fits");
            softly.assertThat(messages.unknownNote(3, null, Language.EN)).contains("3 more games").contains("left them out");
            softly.assertThat(messages.unknownNote(2, 4, Language.NL)).contains("2 andere spellen");
        });
    }

    @Test
    void thousandsAreNotGroupedSoNumbersStayReadableInChips() {
        assertThat(messages.unknownNote(1200, null, Language.EN)).contains("1,200");
    }

    @Test
    void theFixedRepliesExistInBothLanguages() {
        assertSoftly(softly -> {
            softly.assertThat(messages.outOfScope(Language.EN)).contains("What can we play with four people?");
            softly.assertThat(messages.outOfScope(Language.NL)).contains("Wat kunnen we spelen met vier mensen?");
            softly.assertThat(messages.noMatchGroupTooLarge(40, 8, Language.EN)).contains("40").contains("8");
            softly.assertThat(messages.rephrase()).contains("English or Dutch");
            softly.assertThat(messages.emptyLibrary(Language.NL)).isNotBlank();
        });
    }

    @Test
    void anyOtherLanguageFallsBackToEnglish() {
        assertThat(messages.clarify(Language.OTHER)).isEqualTo(messages.clarify(Language.EN));
    }
}
