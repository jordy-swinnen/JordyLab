package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {

    @Test
    void buildGame() {
        Game game = GameTestBuilder.aDefaultGame();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getId()).isNotNull();
            softly.assertThat(game.getTitle()).isEqualTo(GameTestBuilder.DEFAULT_TITLE);
            softly.assertThat(game.getExternalRef()).isEqualTo(GameTestBuilder.DEFAULT_EXTERNAL_REF);
            softly.assertThat(game.getPlatform()).isEqualTo(GameTestBuilder.DEFAULT_PLATFORM);
            softly.assertThat(game.getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(game.getArtworkStatus()).isEqualTo(ArtworkStatus.PENDING);
        });
    }

    @Test
    void buildWithoutTitle() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().title(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankTitle() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().title(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutExternalRef() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().externalRef(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutSource() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().source(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithOutOfRangeMaxLocalPlayers() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().maxLocalPlayers(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameTestBuilder.aGame().maxLocalPlayers(65).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markUninstalledThenSeenAgainRestoresInstalled() {
        Game game = GameTestBuilder.aDefaultGame();

        game.markUninstalled(GameTestBuilder.DEFAULT_LAST_SEEN);
        assertThat(game.getPresence()).isEqualTo(Presence.UNINSTALLED);
        assertThat(game.getUninstalledAt()).isEqualTo(GameTestBuilder.DEFAULT_LAST_SEEN);

        game.seenAgain(GameTestBuilder.DEFAULT_LAST_SEEN.plusSeconds(3600));
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(game.getUninstalledAt()).isNull();
            softly.assertThat(game.getLastSeenAt()).isEqualTo(GameTestBuilder.DEFAULT_LAST_SEEN.plusSeconds(3600));
        });
    }

    @Test
    void recordEnrichmentFailureFailsAfterMaxAttempts() {
        Game game = GameTestBuilder.aDefaultGame();

        game.recordEnrichmentFailure(3);
        assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
        game.recordEnrichmentFailure(3);
        game.recordEnrichmentFailure(3);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.FAILED);
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(3);
        });
    }

    @Test
    void applyEnrichmentStoresFactsAndProse() {
        Game game = GameTestBuilder.aDefaultGame();

        game.applyEnrichment("Platformer", 2, false, true, "A classic side-scrolling platformer.");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getGenre()).isEqualTo("Platformer");
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(2);
            softly.assertThat(game.getOnlineMultiplayer()).isFalse();
            softly.assertThat(game.getSinglePlayer()).isTrue();
            softly.assertThat(game.getDescription()).isEqualTo("A classic side-scrolling platformer.");
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
        });
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(Game.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
