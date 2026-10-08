package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import dev.jordy.jordylab.gamecatalog.service.SteamMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactsStepTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Instant RETRY_BEFORE = NOW.minusSeconds(24 * 3600);

    @Mock
    private SteamAppDetailsClient steamClient;

    @Mock
    private SteamMetadataService steamMetadataService;

    @Mock
    private IgdbClient igdbClient;

    @Mock
    private GameRepository gameRepository;

    private FactsStep step;

    @BeforeEach
    void setUp() {
        GameCatalogProperties properties = new GameCatalogProperties(null, 30, null, null, null, null,
                new GameCatalogProperties.Library(60, 7, 1, 10000));
        step = new FactsStep(steamClient, steamMetadataService, igdbClient, gameRepository, properties);
    }

    private static IgdbClient.IgdbFacts facts() {
        return new IgdbClient.IgdbFacts(76L, "Super Mario World", 1990, List.of("Platformer", "Action"), "Nintendo EAD",
                "Nintendo", "summary", null, null, new IgdbClient.MultiplayerMode(true, false, false, 2));
    }

    @Test
    void aSteamGameNeedsFactsOnlyWhileItsStoreMetadataIsPending() {
        Game pending = Game.builder().title("Portal 2").steamAppId("620").build();
        Game done = Game.builder().title("Portal 2").steamAppId("620").build();
        done.markMetadataFetched();

        assertSoftly(softly -> {
            softly.assertThat(step.needed(pending, RETRY_BEFORE)).isTrue();
            softly.assertThat(step.needed(done, RETRY_BEFORE)).isFalse();
        });
    }

    @Test
    void aGameWithoutSteamNeedsFactsUntilLookedUpAndThenOnlyWhenIncompleteAfterTheRetryPeriod() {
        Game neverLookedUp = Game.builder().title("Super Mario World").build();
        Game recentlyLookedUp = Game.builder().title("Obscure").build();
        recentlyLookedUp.recordFactsChecked(NOW.minusSeconds(60));
        Game oldAndIncomplete = Game.builder().title("Obscure").build();
        oldAndIncomplete.recordFactsChecked(NOW.minusSeconds(3 * 24 * 3600));
        Game oldAndComplete = Game.builder().title("Complete").genres("Action").developer("Dev").releaseYear(2000).build();
        oldAndComplete.recordFactsChecked(NOW.minusSeconds(3 * 24 * 3600));

        assertSoftly(softly -> {
            softly.assertThat(step.needed(neverLookedUp, RETRY_BEFORE)).isTrue();
            softly.assertThat(step.needed(recentlyLookedUp, RETRY_BEFORE)).isFalse();
            softly.assertThat(step.needed(oldAndIncomplete, RETRY_BEFORE)).isTrue();
            softly.assertThat(step.needed(oldAndComplete, RETRY_BEFORE)).isFalse();
        });
    }

    @Test
    void aSteamGameAsksTheSteamStoreAndTheAnswerIsAppliedThroughTheMetadataService() {
        Game game = Game.builder().title("Portal 2").steamAppId("620").build();
        Optional<SteamAppDetailsClient.SteamMetadata> answer = Optional.of(new SteamAppDetailsClient.SteamMetadata(
                "Puzzle", "Valve", "Valve", 2011, "A puzzle game.", "game", null));
        when(steamClient.fetch("620")).thenReturn(answer);

        FactsStep.Fetched fetched = step.fetch(game, List.of("Steam"), false);
        step.apply(game, fetched, NOW);

        assertThat(fetched.attempted()).isTrue();
        verify(steamMetadataService).apply(game, answer);
        verifyNoInteractions(igdbClient);
    }

    @Test
    void aPausedSteamLeavesTheGameUntouchedForALaterRun() {
        Game game = Game.builder().title("Portal 2").steamAppId("620").build();

        FactsStep.Fetched fetched = step.fetch(game, List.of("Steam"), true);
        step.apply(game, fetched, NOW);

        assertThat(fetched.attempted()).isFalse();
        assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
        verifyNoInteractions(steamClient, steamMetadataService);
    }

    @Test
    void aSteamRateLimitIsReportedSoTheRunCanSlowDown() {
        Game game = Game.builder().title("Portal 2").steamAppId("620").build();
        when(steamClient.fetch("620")).thenThrow(new SteamRateLimitedException("429"));

        FactsStep.Fetched fetched = step.fetch(game, List.of("Steam"), false);

        assertSoftly(softly -> {
            softly.assertThat(fetched.steamRateLimited()).isTrue();
            softly.assertThat(fetched.attempted()).isFalse();
        });
    }

    @Test
    void aRomIsFoundOnIgdbByTitleOnItsPlatformAndTheFactsFillWhatIsEmpty() {
        Game game = Game.builder().title("Super Mario World").build();
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGame("Super Mario World", 19L)).thenReturn(Optional.of(new IgdbClient.IgdbGame(76L, "Super Mario World")));
        when(igdbClient.fetchFacts(76L)).thenReturn(Optional.of(facts()));
        when(gameRepository.findByIgdbGameId("76")).thenReturn(Optional.empty());

        FactsStep.Fetched fetched = step.fetch(game, List.of("SNES"), false);
        step.apply(game, fetched, NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getGenres()).isEqualTo("Platformer, Action");
            softly.assertThat(game.getDeveloper()).isEqualTo("Nintendo EAD");
            softly.assertThat(game.getPublisher()).isEqualTo("Nintendo");
            softly.assertThat(game.getReleaseYear()).isEqualTo(1990);
            softly.assertThat(game.getIgdbGameId()).isEqualTo("76");
            softly.assertThat(game.getLocalMultiplayer()).isTrue();
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(2);
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.IGDB);
            softly.assertThat(game.getFactsCheckedAt()).isEqualTo(NOW);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.OK);
        });
    }

    @Test
    void anIgdbIdAlreadyHeldByAnotherGameIsNotTakenOver() {
        Game game = Game.builder().title("Super Mario World").build();
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGame("Super Mario World", null)).thenReturn(Optional.of(new IgdbClient.IgdbGame(76L, "x")));
        when(igdbClient.fetchFacts(76L)).thenReturn(Optional.of(facts()));
        when(gameRepository.findByIgdbGameId("76")).thenReturn(Optional.of(Game.builder().title("Other").build()));

        step.apply(game, step.fetch(game, List.of(), false), NOW);

        assertThat(game.getIgdbGameId()).isNull();
    }

    @Test
    void aGameIgdbDoesNotKnowIsMarkedAsLookedUpSoItIsNotAskedAgainBeforeTheRetryPeriod() {
        Game game = Game.builder().title("Homebrew Thing").build();
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGame("Homebrew Thing", null)).thenReturn(Optional.empty());

        step.apply(game, step.fetch(game, List.of(), false), NOW);

        assertSoftly(softly -> {
            softly.assertThat(game.getFactsCheckedAt()).isEqualTo(NOW);
            softly.assertThat(game.getGenres()).isNull();
        });
    }

    @Test
    void withoutIgdbConfiguredNothingIsAskedOrRecorded() {
        Game game = Game.builder().title("Super Mario World").build();
        when(igdbClient.isConfigured()).thenReturn(false);

        FactsStep.Fetched fetched = step.fetch(game, List.of("SNES"), false);
        step.apply(game, fetched, NOW);

        assertThat(game.getFactsCheckedAt()).isNull();
    }
}
