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
            softly.assertThat(game.getPlatform()).isEqualTo(GameTestBuilder.DEFAULT_PLATFORM);
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.PENDING);
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.PENDING);
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
    void buildWithoutPlatform() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().platform(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithTooLongSteamAppId() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().steamAppId("x".repeat(33)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithTooLongGenres() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().genres("x".repeat(201)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithOutOfRangeReleaseYear() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().releaseYear(1949).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameTestBuilder.aGame().releaseYear(2029).build())
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
    void recordMetadataFailureFailsAfterMaxAttempts() {
        Game game = GameTestBuilder.aDefaultGame();

        game.recordMetadataFailure(3);
        assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
        game.recordMetadataFailure(3);
        game.recordMetadataFailure(3);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.FAILED);
            softly.assertThat(game.getMetadataAttempts()).isEqualTo(3);
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
    void applyDeterministicMetadataStoresFieldsAndMarksOk() {
        Game game = GameTestBuilder.aDefaultGame();

        game.applyDeterministicMetadata("Platformer, Action", "Nintendo", "Nintendo", 1990);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getGenres()).isEqualTo("Platformer, Action");
            softly.assertThat(game.getDeveloper()).isEqualTo("Nintendo");
            softly.assertThat(game.getPublisher()).isEqualTo("Nintendo");
            softly.assertThat(game.getReleaseYear()).isEqualTo(1990);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.OK);
        });
    }

    @Test
    void applyCoverAndBannerArtworkAreIndependent() {
        Game game = GameTestBuilder.aDefaultGame();

        game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.test/cover.jpg");
        game.applyBannerArtwork(ArtworkStatus.PLACEHOLDER, null);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getCoverRef()).isEqualTo("https://example.test/cover.jpg");
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
            softly.assertThat(game.getBannerRef()).isNull();
        });
    }

    @Test
    void requestLocalCoverFallbackMarksStatusAndCountsRequests() {
        Game game = GameTestBuilder.aDefaultGame();

        game.requestLocalCoverFallback();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.LOCAL_FALLBACK_REQUESTED);
            softly.assertThat(game.getArtworkFallbackRequests()).isEqualTo(1);
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
