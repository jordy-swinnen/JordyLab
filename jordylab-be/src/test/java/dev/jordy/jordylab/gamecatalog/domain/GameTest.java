package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {

    private static final Instant WRITTEN_AT = Instant.parse("2026-10-07T10:12:00Z");
    private static final AiAuthorship AUTHORSHIP =
            AiAuthorship.of("anthropic/claude-haiku-4.5", "anthropic/claude-haiku-4.5", WRITTEN_AT);

    @Test
    void buildGame() {
        Game game = GameTestBuilder.aDefaultGame();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getId()).isNotNull();
            softly.assertThat(game.getTitle()).isEqualTo(GameTestBuilder.DEFAULT_TITLE);
            softly.assertThat(game.getTitleKey()).isEqualTo("super mario world");
            softly.assertThat(game.getDescriptionSource()).isNull();
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
    void buildWithTooLongSteamAppId() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().steamAppId("x".repeat(33)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithTooLongIgdbGameId() {
        assertThatThrownBy(() -> GameTestBuilder.aGame().igdbGameId("x".repeat(33)).build())
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

        game.applyEnrichment("Platformer", false, true, "A classic side-scrolling platformer.", AUTHORSHIP);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getGenre()).isEqualTo("Platformer");
            softly.assertThat(game.getOnlineMultiplayer()).isFalse();
            softly.assertThat(game.getSinglePlayer()).isTrue();
            softly.assertThat(game.getDescription()).isEqualTo("A classic side-scrolling platformer.");
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
        });
    }

    @Test
    void applyDeterministicMultiplayerSetsFactsSourceAndClearsAttempts() {
        Game game = GameTestBuilder.aDefaultGame();
        game.recordMultiplayerFailure();
        game.recordMultiplayerFailure();

        game.applyDeterministicMultiplayer(true, true, 4, MultiplayerSource.IGDB);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getLocalMultiplayer()).isTrue();
            softly.assertThat(game.getSplitScreen()).isTrue();
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(4);
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.IGDB);
            softly.assertThat(game.getMultiplayerAttempts()).isZero();
        });
    }

    @Test
    void applyDeterministicMultiplayerKeepsMaxPlayersWhenNull() {
        Game game = Game.builder().steamAppId("620").title("Portal 2")
                .maxLocalPlayers(4).build();

        game.applyDeterministicMultiplayer(true, true, null, MultiplayerSource.STEAM);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(4);
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.STEAM);
        });
    }

    @Test
    void recordMultiplayerFailureIncrementsAndResetClears() {
        Game game = GameTestBuilder.aDefaultGame();

        game.recordMultiplayerFailure();
        game.recordMultiplayerFailure();
        assertThat(game.getMultiplayerAttempts()).isEqualTo(2);

        game.resetMultiplayerForRetry();

        assertThat(game.getMultiplayerAttempts()).isZero();
    }

    @Test
    void applyDeterministicMetadataFillsFieldsWithoutTouchingStatus() {
        Game game = GameTestBuilder.aDefaultGame();

        game.applyDeterministicMetadata("Platformer, Action", "Nintendo", "Nintendo", 1990);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getGenres()).isEqualTo("Platformer, Action");
            softly.assertThat(game.getDeveloper()).isEqualTo("Nintendo");
            softly.assertThat(game.getPublisher()).isEqualTo("Nintendo");
            softly.assertThat(game.getReleaseYear()).isEqualTo(1990);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
        });
    }

    @Test
    void applyDeterministicMetadataOnlyFillsNullFields() {
        Game game = GameTestBuilder.aDefaultGame();
        game.applyDeterministicMetadata("Steam Genres", "Steam Dev", "Steam Pub", 2011);

        game.applyDeterministicMetadata("AI Genres", "AI Dev", "AI Pub", 1999);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getGenres()).isEqualTo("Steam Genres");
            softly.assertThat(game.getDeveloper()).isEqualTo("Steam Dev");
            softly.assertThat(game.getPublisher()).isEqualTo("Steam Pub");
            softly.assertThat(game.getReleaseYear()).isEqualTo(2011);
        });
    }

    @Test
    void markMetadataFetchedMarksOkWithoutData() {
        Game game = GameTestBuilder.aDefaultGame();

        game.markMetadataFetched();

        assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.OK);
    }

    @Test
    void applyDeterministicMultiplayerFlagsOnlyFillsNulls() {
        Game game = GameTestBuilder.aDefaultGame();
        game.applyDeterministicMultiplayerFlags(true, false);

        game.applyDeterministicMultiplayerFlags(false, true);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getSinglePlayer()).isTrue();
            softly.assertThat(game.getOnlineMultiplayer()).isFalse();
        });
    }

    @Test
    void updateCatalogInfoRespectsTitleAuthority() {
        Game game = Game.builder().steamAppId("620").title("Library Name")
                .titleSource(TitleSource.LIBRARY).build();

        game.updateCatalogInfo("Manifest Name", TitleSource.MANIFEST);

        assertThat(game.getTitle()).isEqualTo("Library Name");

        game.updateCatalogInfo("New Library Name", TitleSource.LIBRARY);

        assertThat(game.getTitle()).isEqualTo("New Library Name");
    }

    @Test
    void manualTitleSourceOutranksLibrary() {
        Game game = Game.builder().title("Library Name")
                .titleSource(TitleSource.LIBRARY).build();

        game.updateCatalogInfo("Handheld Name", TitleSource.MANUAL);

        assertThat(game.getTitle()).isEqualTo("Handheld Name");
        assertThat(game.getTitleSource()).isEqualTo(TitleSource.MANUAL);
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
    void applyEnrichmentRecordsTheAnsweringModelAndTheTime() {
        Game game = GameTestBuilder.aDefaultGame();

        game.applyEnrichment("Platformer", false, true, "A classic platformer.", AUTHORSHIP);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getDescriptionSource()).isEqualTo(DescriptionSource.AI);
            softly.assertThat(game.getDescriptionModel()).isEqualTo("anthropic/claude-haiku-4.5");
            softly.assertThat(game.getDescriptionRequestedModel()).isNull();
            softly.assertThat(game.getDescriptionWrittenAt()).isEqualTo(WRITTEN_AT);
        });
    }

    @Test
    void applyEnrichmentKeepsTheRouterThatWasSelectedWhenItPickedAnotherModel() {
        Game game = GameTestBuilder.aDefaultGame();

        game.applyEnrichment("Platformer", false, true, "A classic platformer.",
                AiAuthorship.of("anthropic/claude-sonnet-5", "jev-router", WRITTEN_AT));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getDescriptionModel()).isEqualTo("anthropic/claude-sonnet-5");
            softly.assertThat(game.getDescriptionRequestedModel()).isEqualTo("jev-router");
        });
    }

    @Test
    void applyEnrichmentWithoutAReportedModelStoresNoModelButKeepsTheSelectedId() {
        Game game = GameTestBuilder.aDefaultGame();

        game.applyEnrichment("Platformer", false, true, "A classic platformer.",
                AiAuthorship.of(null, "jev-router", WRITTEN_AT));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getDescriptionModel()).isNull();
            softly.assertThat(game.getDescriptionRequestedModel()).isEqualTo("jev-router");
        });
    }

    @Test
    void aSteamDescriptionIsRecordedAsSteamWithNoModel() {
        Game game = Game.builder().steamAppId("620").title("Portal 2").build();

        game.applyDeterministicDescription("Portal 2 is a puzzle game.");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getDescription()).isEqualTo("Portal 2 is a puzzle game.");
            softly.assertThat(game.getDescriptionSource()).isEqualTo(DescriptionSource.STEAM);
            softly.assertThat(game.getDescriptionModel()).isNull();
            softly.assertThat(game.getDescriptionRequestedModel()).isNull();
        });
    }

    @Test
    void aSteamDescriptionNeverReplacesAnExistingOne() {
        Game game = GameTestBuilder.aDefaultGame();
        game.applyEnrichment("Platformer", false, true, "AI text.", AUTHORSHIP);

        game.applyDeterministicDescription("Steam text.");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getDescription()).isEqualTo("AI text.");
            softly.assertThat(game.getDescriptionSource()).isEqualTo(DescriptionSource.AI);
        });
    }

    @Test
    void theTitleKeyFollowsTheTitleWhenTheTitleChanges() {
        Game game = Game.builder().steamAppId("620").title("Library Name")
                .titleSource(TitleSource.LIBRARY).build();

        game.updateCatalogInfo("New Library Name (USA)", TitleSource.LIBRARY);

        assertThat(game.getTitleKey()).isEqualTo("new library name");
    }

    @Test
    void theTitleKeyFallsBackToTheLowerCasedTitleWhenNormalisingEmptiesIt() {
        assertThat(Game.builder().title("(USA)").build().getTitleKey()).isEqualTo("(usa)");
    }

    @Test
    void recordFactsAndArtworkChecksStoreTheTimes() {
        Game game = GameTestBuilder.aDefaultGame();

        game.recordFactsChecked(WRITTEN_AT);
        game.recordArtworkChecked(WRITTEN_AT.plusSeconds(60));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(game.getFactsCheckedAt()).isEqualTo(WRITTEN_AT);
            softly.assertThat(game.getArtworkCheckedAt()).isEqualTo(WRITTEN_AT.plusSeconds(60));
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
