package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.service.libbot.AppliedConstraint.Kind;
import dev.jordy.jordylab.gamecatalog.service.libbot.QuestionInterpretation.Facts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ConstraintDeriverTest {

    private static final LibraryVocabulary VOCABULARY = new LibraryVocabulary(
            List.of("PlayStation 5", "Steam", "Nintendo Switch"), List.of("Living room PC", "Switch dock"), 4);

    private final ConstraintDeriver deriver = new ConstraintDeriver();

    private static Facts facts(Integer partySize, Boolean alone, Boolean online, List<String> platforms,
            List<String> places) {
        return new Facts(partySize, alone, online, platforms, places, null, null, null, null, null, null, null, null,
                null);
    }

    @Test
    void sixPeopleMeansSixOrMoreLocalPlayersOnOneScreen() {
        Derivation derivation = deriver.derive(facts(6, null, null, null, null), VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.constraints().minLocalPlayers()).isEqualTo(6);
            softly.assertThat(derivation.constraints().requireLocalMultiplayer()).isTrue();
            softly.assertThat(derivation.applied()).containsExactly(
                    AppliedConstraint.of(Kind.MIN_LOCAL_PLAYERS, "6"), AppliedConstraint.of(Kind.LOCAL_MULTIPLAYER));
            softly.assertThat(derivation.partyExceedsKnownGroups()).isTrue();
            softly.assertThat(derivation.largestKnownGroup()).isEqualTo(4);
        });
    }

    @Test
    void aMarkFilterBecomesAChipKeyedByMarkAndWhoseMarkItIs() {
        Facts facts = new Facts(null, null, null, null, null, null, null, null, null, MarkType.PLAYED_LIKED,
                MarkScope.MINE, null, null, null);

        Derivation derivation = deriver.derive(facts, VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.applied()).containsExactly(AppliedConstraint.of(Kind.MARK, "PLAYED_LIKED.MINE"));
            softly.assertThat(derivation.constraints().markFilter()).isEqualTo(MarkType.PLAYED_LIKED);
            softly.assertThat(derivation.constraints().markScope()).isEqualTo(MarkScope.MINE);
        });
    }

    @Test
    void somethingLikeWhatILikedIsOneChipAndAFlag() {
        Facts facts = new Facts(null, null, null, null, null, null, null, null, null, null, null, true, null, null);

        Derivation derivation = deriver.derive(facts, VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.applied()).containsExactly(AppliedConstraint.of(Kind.LIKE_MY_LIKED));
            softly.assertThat(derivation.constraints().likeMyLiked()).isTrue();
        });
    }

    @Test
    void fourPeopleFitsTheLargestKnownGroup() {
        Derivation derivation = deriver.derive(facts(4, null, null, null, null), VOCABULARY);

        assertThat(derivation.partyExceedsKnownGroups()).isFalse();
    }

    @Test
    void justMeMeansSinglePlayer() {
        Derivation derivation = deriver.derive(facts(null, true, null, null, null), VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.constraints().requireSinglePlayer()).isTrue();
            softly.assertThat(derivation.constraints().minLocalPlayers()).isNull();
            softly.assertThat(derivation.applied()).containsExactly(AppliedConstraint.of(Kind.SINGLE_PLAYER));
        });
    }

    @Test
    void aPartyOfOneIsPlayingAlone() {
        assertThat(deriver.derive(facts(1, null, null, null, null), VOCABULARY).constraints().requireSinglePlayer())
                .isTrue();
    }

    @Test
    void aGroupOverridesAContradictoryAlone() {
        Derivation derivation = deriver.derive(facts(3, true, null, null, null), VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.constraints().requireSinglePlayer()).isFalse();
            softly.assertThat(derivation.constraints().minLocalPlayers()).isEqualTo(3);
        });
    }

    @Test
    void onlineWithFriendsMeansOnlineMultiplayer() {
        Derivation derivation = deriver.derive(facts(null, null, true, null, null), VOCABULARY);

        assertThat(derivation.applied()).containsExactly(AppliedConstraint.of(Kind.ONLINE));
    }

    @Test
    void platformAliasesResolveAndUnknownNamesAreDropped() {
        Derivation derivation = deriver.derive(facts(null, null, null, List.of("PS5", "Dreamcast", "steam"), null),
                VOCABULARY);

        assertThat(derivation.constraints().platforms()).containsExactly("PlayStation 5", "Steam");
    }

    @Test
    void placesAreMatchedByLabelIgnoringCaseAndUnknownOnesDropped() {
        Derivation derivation = deriver.derive(
                facts(null, null, null, null, List.of("living room pc", "The attic")), VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.constraints().places()).containsExactly("Living room PC");
            softly.assertThat(derivation.applied()).containsExactly(AppliedConstraint.of(Kind.PLACE, "Living room PC"));
        });
    }

    @Test
    void yearsAreSwappedWhenGivenBackwards() {
        Facts backwards = new Facts(null, null, null, null, null, null, null, 1999, 1990, null, null, null, null, null);

        Derivation derivation = deriver.derive(backwards, VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.constraints().releaseYearMin()).isEqualTo(1990);
            softly.assertThat(derivation.constraints().releaseYearMax()).isEqualTo(1999);
            softly.assertThat(derivation.applied()).containsExactly(AppliedConstraint.of(Kind.YEARS, "1990-1999"));
        });
    }

    @Test
    void genresAreLowercasedDistinctAndCapped() {
        Facts many = new Facts(null, null, null, null, null, null,
                List.of("Racing", "racing", "Puzzle", "RPG", "Action", "Sports", "Strategy"), null, null, null, null,
                null, null, null);

        assertThat(deriver.derive(many, VOCABULARY).constraints().genres())
                .containsExactly("racing", "puzzle", "rpg", "action", "sports");
    }

    @Test
    void noFactsMeansNoRequirements() {
        Derivation derivation = deriver.derive(Facts.none(), VOCABULARY);

        assertSoftly(softly -> {
            softly.assertThat(derivation.applied()).isEmpty();
            softly.assertThat(derivation.constraints().hasFactRequirements()).isFalse();
            softly.assertThat(derivation.partyExceedsKnownGroups()).isFalse();
        });
    }

    @Test
    void anUnknownLargestGroupNeverMakesAPartyTooLarge() {
        Derivation derivation = deriver.derive(facts(40, null, null, null, null),
                new LibraryVocabulary(List.of(), List.of(), null));

        assertThat(derivation.partyExceedsKnownGroups()).isFalse();
    }
}
