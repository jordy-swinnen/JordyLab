package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiplayerServiceTest {

    private static final int BATCH = 25;
    private static final int MAX_ATTEMPTS = 3;
    private static final PageRequest FIRST_BATCH = PageRequest.of(0, BATCH);

    @Mock
    private GameRepository gameRepository;

    @Mock
    private SteamAppDetailsClient steamAppDetailsClient;

    @Mock
    private IgdbClient igdbClient;

    private MultiplayerService multiplayerService;

    @BeforeEach
    void setUp() {
        multiplayerService = new MultiplayerService(gameRepository, steamAppDetailsClient, igdbClient, properties());
    }

    @Test
    void steamCategoriesResolveToSteamSource() {
        Game game = aSteamGame("620", "Portal 2");
        stubBacklog(game);
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.of(new SteamAppDetailsClient.SteamMetadata(
                null, null, null, null, null, "game",
                new SteamAppDetailsClient.MultiplayerFacts(true, true, true, true, true))));

        int processed = multiplayerService.derivePending(BATCH);

        assertThat(processed).isEqualTo(1);
        assertSoftly(softly -> {
            softly.assertThat(game.getLocalMultiplayer()).isTrue();
            softly.assertThat(game.getSplitScreen()).isTrue();
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.STEAM);
            softly.assertThat(game.getMultiplayerAttempts()).isZero();
        });
    }

    @Test
    void romResolvesThroughIgdb() {
        Game game = Game.builder().platform("SNES").title("Super Mario World").build();
        stubBacklog(game);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Super Mario World"))
                .thenReturn(Optional.of(new IgdbClient.MultiplayerMode(true, true, false, 2)));

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getLocalMultiplayer()).isTrue();
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(2);
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.IGDB);
        });
    }

    @Test
    void noIgdbMatchIncrementsAttempts() {
        Game game = Game.builder().platform("SNES").title("Obscure ROM").build();
        stubBacklog(game);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Obscure ROM")).thenReturn(Optional.empty());

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.UNKNOWN);
            softly.assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        });
    }

    @Test
    void unconfiguredIgdbIncrementsAttempts() {
        Game game = Game.builder().platform("SNES").title("Super Mario World").build();
        stubBacklog(game);
        when(igdbClient.isConfigured()).thenReturn(false);

        multiplayerService.derivePending(BATCH);

        assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
    }

    @Test
    void steamRateLimitPausesTheBatchWithoutRecordingAFailure() {
        Game first = aSteamGame("620", "Portal 2");
        Game second = aSteamGame("440", "Team Fortress 2");
        stubBacklog(first, second);
        when(steamAppDetailsClient.fetch("620")).thenThrow(new SteamRateLimitedException("429"));

        int processed = multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(processed).isZero();
            softly.assertThat(first.getMultiplayerAttempts()).isZero();
            softly.assertThat(second.getMultiplayerAttempts()).isZero();
        });
    }

    @Test
    void emptyBacklogReturnsZero() {
        stubBacklog();

        assertThat(multiplayerService.derivePending(BATCH)).isZero();
    }

    @Test
    void backlogPassSkipsRedundantSteamFetchOnceMetadataAlreadyChecked() {
        Game game = Game.builder().platform("Steam").steamAppId("620").title("Portal 2")
                .metadataStatus(MetadataStatus.OK).build();
        stubBacklog(game);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Portal 2")).thenReturn(Optional.empty());

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.UNKNOWN);
            softly.assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        });
        verify(steamAppDetailsClient, never()).fetch("620");
    }

    @Test
    void manualRefreshAlwaysRechecksSteamEvenWhenMetadataAlreadyChecked() {
        Game game = Game.builder().platform("Steam").steamAppId("620").title("Portal 2")
                .metadataStatus(MetadataStatus.OK).build();
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.of(new SteamAppDetailsClient.SteamMetadata(
                null, null, null, null, null, "game",
                new SteamAppDetailsClient.MultiplayerFacts(true, true, true, true, true))));

        multiplayerService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.STEAM);
            softly.assertThat(game.getLocalMultiplayer()).isTrue();
        });
        verify(steamAppDetailsClient).fetch("620");
    }

    private void stubBacklog(Game... games) {
        when(gameRepository.findMultiplayerBacklog(eq(MAX_ATTEMPTS), eq(FIRST_BATCH)))
                .thenReturn(List.of(games));
    }

    private Game aSteamGame(String appId, String title) {
        return Game.builder().platform("Steam").steamAppId(appId).title(title).build();
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(8, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(BATCH, MAX_ATTEMPTS),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5),
                new GameCatalogProperties.Library(720, 14, 1500, 10000));
    }
}
