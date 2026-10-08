package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.client.ArtworkLookupClient;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtworkStepTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Instant RETRY_BEFORE = NOW.minusSeconds(24 * 3600);

    @Mock
    private ArtworkLookupClient lookupClient;

    @Mock
    private IgdbClient igdbClient;

    private ArtworkStep stepWith(boolean lookupEnabled) {
        GameCatalogProperties properties = new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, lookupEnabled, 2000L), 30, null, null, null,
                null, null);

        return new ArtworkStep(lookupClient, igdbClient, properties);
    }

    private static IgdbClient.IgdbFacts igdbArt() {
        return new IgdbClient.IgdbFacts(76L, "Super Mario World", null, List.of(), null, null, null,
                "https://images.igdb.com/cover.jpg", "https://images.igdb.com/banner.jpg", null);
    }

    @Test
    void aGameNeedsArtworkWhileACoverOrBannerIsOpenAndTheLastLookUpIsOld() {
        ArtworkStep step = stepWith(true);
        Game fresh = Game.builder().title("Hades").build();
        Game recentlyChecked = Game.builder().title("Hades").build();
        recentlyChecked.recordArtworkChecked(NOW.minusSeconds(60));
        Game oldPlaceholder = Game.builder().title("Hades").build();
        oldPlaceholder.applyCoverArtwork(ArtworkStatus.PLACEHOLDER, null);
        oldPlaceholder.recordArtworkChecked(NOW.minusSeconds(3 * 24 * 3600));
        Game complete = Game.builder().title("Hades").build();
        complete.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://x/c.jpg");
        complete.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, "https://x/b.jpg");

        assertSoftly(softly -> {
            softly.assertThat(step.needed(fresh, RETRY_BEFORE)).isTrue();
            softly.assertThat(step.needed(recentlyChecked, RETRY_BEFORE)).isFalse();
            softly.assertThat(step.needed(oldPlaceholder, RETRY_BEFORE)).isTrue();
            softly.assertThat(step.needed(complete, RETRY_BEFORE)).isFalse();
        });
    }

    @Test
    void nothingIsLookedUpWhenExternalLookupIsSwitchedOff() {
        assertThat(stepWith(false).needed(Game.builder().title("Hades").build(), RETRY_BEFORE)).isFalse();
    }

    @Test
    void aSteamGameTakesTheSteamCdnFirstAndNeverAsksIgdb() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Portal 2").steamAppId("620").build();
        when(lookupClient.findCoverArtworkUrl(SourceType.STEAM, "Steam", "620", "Portal 2"))
                .thenReturn(Optional.of("https://cdn/cover.jpg"));
        when(lookupClient.findBannerArtworkUrl(SourceType.STEAM, "Steam", "620", "Portal 2"))
                .thenReturn(Optional.of("https://cdn/banner.jpg"));

        ArtworkStep.Fetched fetched = step.fetch(game, List.of("Steam"), FactsStep.Fetched.none());
        step.apply(game, fetched, NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getCoverRef()).isEqualTo("https://cdn/cover.jpg");
            softly.assertThat(game.getBannerRef()).isEqualTo("https://cdn/banner.jpg");
            softly.assertThat(game.getArtworkCheckedAt()).isEqualTo(NOW);
        });
        verifyNoInteractions(igdbClient);
    }

    @Test
    void aRomTakesIgdbArtFromTheFactsAlreadyFetchedBeforeTryingLibretro() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Super Mario World").build();
        FactsStep.Fetched facts = new FactsStep.Fetched(Optional.empty(), Optional.of(igdbArt()), true, false);

        ArtworkStep.Fetched fetched = step.fetch(game, List.of("SNES"), facts);
        step.apply(game, fetched, NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverRef()).isEqualTo("https://images.igdb.com/cover.jpg");
            softly.assertThat(game.getBannerRef()).isEqualTo("https://images.igdb.com/banner.jpg");
        });
        verifyNoInteractions(lookupClient);
    }

    @Test
    void whenIgdbHasNothingLibretroIsTriedForEachPlatformOfTheGame() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Super Mario World").build();
        when(igdbClient.isConfigured()).thenReturn(false);
        when(lookupClient.findCoverArtworkUrl(SourceType.EMUDECK, "SNES", null, "Super Mario World"))
                .thenReturn(Optional.empty());
        when(lookupClient.findCoverArtworkUrl(SourceType.EMUDECK, "Super Famicom", null, "Super Mario World"))
                .thenReturn(Optional.of("https://libretro/cover.png"));
        when(lookupClient.findBannerArtworkUrl(SourceType.EMUDECK, "SNES", null, "Super Mario World"))
                .thenReturn(Optional.empty());
        when(lookupClient.findBannerArtworkUrl(SourceType.EMUDECK, "Super Famicom", null, "Super Mario World"))
                .thenReturn(Optional.empty());

        step.apply(game, step.fetch(game, List.of("SNES", "Super Famicom"), FactsStep.Fetched.none()), NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getCoverRef()).isEqualTo("https://libretro/cover.png");
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        });
    }

    @Test
    void afterEverySourceMissesTheGameGetsAnExplicitPlaceholderAndIsMarkedChecked() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Homebrew").build();
        when(igdbClient.isConfigured()).thenReturn(false);

        step.apply(game, step.fetch(game, List.of(), FactsStep.Fetched.none()), NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
            softly.assertThat(game.getArtworkCheckedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void anUploadedCoverIsNeverReplaced() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Hades").build();
        game.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, "hades.png");
        when(igdbClient.isConfigured()).thenReturn(false);

        step.apply(game, step.fetch(game, List.of(), FactsStep.Fetched.none()), NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.LOCAL_UPLOAD);
            softly.assertThat(game.getCoverRef()).isEqualTo("hades.png");
        });
    }

    @Test
    void aNotAttemptedFetchChangesNothing() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Hades").build();

        step.apply(game, ArtworkStep.Fetched.none(), NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.PENDING);
            softly.assertThat(game.getArtworkCheckedAt()).isNull();
        });
    }

    @Test
    void aRefreshReplacesAnExternalCoverWhenTheNewLookupFindsOne() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Hades").steamAppId("1145360").build();
        game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://old/cover.jpg");
        game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, "https://old/banner.jpg");
        when(lookupClient.findCoverArtworkUrl(SourceType.STEAM, "Steam", "1145360", "Hades"))
                .thenReturn(Optional.of("https://new/cover.jpg"));
        when(lookupClient.findBannerArtworkUrl(SourceType.STEAM, "Steam", "1145360", "Hades"))
                .thenReturn(Optional.of("https://new/banner.jpg"));

        step.apply(game, step.fetch(game, List.of("Steam"), FactsStep.Fetched.none(), true), NOW, true);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverRef()).isEqualTo("https://new/cover.jpg");
            softly.assertThat(game.getBannerRef()).isEqualTo("https://new/banner.jpg");
        });
    }

    @Test
    void aRefreshThatFindsNothingKeepsTheExistingExternalCoverInsteadOfDowngrading() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Hades").build();
        game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://old/cover.jpg");
        game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, "https://old/banner.jpg");
        when(igdbClient.isConfigured()).thenReturn(false);

        step.apply(game, step.fetch(game, List.of(), FactsStep.Fetched.none(), true), NOW, true);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getCoverRef()).isEqualTo("https://old/cover.jpg");
            softly.assertThat(game.getBannerRef()).isEqualTo("https://old/banner.jpg");
        });
    }

    @Test
    void aRefreshNeverTouchesAnUploadedCover() {
        ArtworkStep step = stepWith(true);
        Game game = Game.builder().title("Hades").steamAppId("1145360").build();
        game.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, "hades.png");
        game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, "https://old/banner.jpg");
        when(lookupClient.findBannerArtworkUrl(SourceType.STEAM, "Steam", "1145360", "Hades"))
                .thenReturn(Optional.of("https://new/banner.jpg"));

        step.apply(game, step.fetch(game, List.of("Steam"), FactsStep.Fetched.none(), true), NOW, true);

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.LOCAL_UPLOAD);
            softly.assertThat(game.getCoverRef()).isEqualTo("hades.png");
            softly.assertThat(game.getBannerRef()).isEqualTo("https://new/banner.jpg");
        });
    }
}
