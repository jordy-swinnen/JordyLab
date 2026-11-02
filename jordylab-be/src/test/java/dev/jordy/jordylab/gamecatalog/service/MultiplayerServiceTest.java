package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountGap;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient.PlayerCountLookup;
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
import static org.mockito.Mockito.times;
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

    @Mock
    private TheGamesDbClient theGamesDbClient;

    @Mock
    private GamePlatformService platformService;

    private MultiplayerService multiplayerService;

    @BeforeEach
    void setUp() {
        multiplayerService = new MultiplayerService(gameRepository, steamAppDetailsClient, igdbClient, theGamesDbClient,
                platformService, properties());
    }

    @Test
    void steamCategoriesKeepTheirFlagsAndIgdbFillsThePlayerCount() {
        Game game = aSteamGame("620", "Portal 2");
        stubBacklog(game);
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.of(new SteamAppDetailsClient.SteamMetadata(
                null, null, null, null, null, "game",
                new SteamAppDetailsClient.MultiplayerFacts(true, true, true, true, true))));
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Portal 2"))
                .thenReturn(Optional.of(new IgdbClient.MultiplayerMode(true, false, true, 4)));

        int processed = multiplayerService.derivePending(BATCH);

        assertThat(processed).isEqualTo(1);
        assertSoftly(softly -> {
            softly.assertThat(game.getLocalMultiplayer()).isTrue();
            softly.assertThat(game.getSplitScreen()).isTrue();
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(4);
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.STEAM);
            softly.assertThat(game.getMultiplayerAttempts()).isZero();
        });
    }

    @Test
    void aSteamGameWithoutIgdbDataCountsAnAttemptForTheMissingPlayerCount() {
        Game game = aSteamGame("620", "Portal 2");
        stubBacklog(game);
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.of(new SteamAppDetailsClient.SteamMetadata(
                null, null, null, null, null, "game",
                new SteamAppDetailsClient.MultiplayerFacts(true, true, true, true, true))));
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Portal 2")).thenReturn(Optional.empty());

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.STEAM);
            softly.assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        });
    }

    @Test
    void aModeWithoutPlayerCountsStillCountsAsAnAttempt() {
        Game game = Game.builder().title("Super Mario World").build();
        stubBacklog(game);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Super Mario World"))
                .thenReturn(Optional.of(new IgdbClient.MultiplayerMode(true, true, false, null)));

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getLocalMultiplayer()).isTrue();
            softly.assertThat(game.getMaxLocalPlayers()).isNull();
            softly.assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        });
    }

    @Test
    void aBulkReriveResetsTheAttemptCounterAndDoesNotRecheckSteam() {
        Game game = Game.builder().steamAppId("620").title("Portal 2")
                .metadataStatus(MetadataStatus.OK).build();
        game.recordMultiplayerFailure();
        game.recordMultiplayerFailure();
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Portal 2"))
                .thenReturn(Optional.of(new IgdbClient.MultiplayerMode(true, true, false, 2)));

        multiplayerService.rederive(game.getId());

        assertSoftly(softly -> {
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(2);
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.IGDB);
            softly.assertThat(game.getMultiplayerAttempts()).isZero();
        });
        verify(steamAppDetailsClient, never()).fetch("620");
    }

    @Test
    void romResolvesThroughIgdb() {
        Game game = Game.builder().title("Super Mario World").build();
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
        Game game = Game.builder().title("Obscure ROM").build();
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
        Game game = Game.builder().title("Super Mario World").build();
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
        Game game = Game.builder().steamAppId("620").title("Portal 2")
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
        Game game = Game.builder().steamAppId("620").title("Portal 2")
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

    @Test
    void aConsoleGameWithFlagsButNoCountGetsTheAgreedCountFromTheSecondSource() {
        Game game = aFlaggedConsoleGame("Mario Party 6");
        stubBacklog(game);
        stubIgdbFlagsOnly("Mario Party 6");
        stubSecondSource(game, List.of("GameCube"), PlayerCountLookup.found(4));

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(4);
            softly.assertThat(game.getMaxLocalPlayersSource()).isEqualTo(PlayerCountSource.THEGAMESDB);
            softly.assertThat(game.getMultiplayerSource()).isEqualTo(MultiplayerSource.IGDB);
            softly.assertThat(game.getPlayerCountGap()).isNull();
            softly.assertThat(game.getMultiplayerAttempts()).isZero();
        });
    }

    @Test
    void theSecondSourceIsNeverAskedWhenIgdbAlreadyGaveTheCount() {
        Game game = aFlaggedConsoleGame("Mario Party 6");
        stubBacklog(game);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Mario Party 6"))
                .thenReturn(Optional.of(new IgdbClient.MultiplayerMode(true, true, false, 4)));

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getMaxLocalPlayersSource()).isEqualTo(PlayerCountSource.IGDB);
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(4);
        });
        verify(theGamesDbClient, never()).lookup("Mario Party 6", game.getTitleKey(), "GameCube");
    }

    @Test
    void aSteamGameIsNeverAskedInTheSecondSource() {
        Game game = Game.builder().steamAppId("620").title("Portal 2").localMultiplayer(true)
                .multiplayerSource(MultiplayerSource.STEAM).metadataStatus(MetadataStatus.OK).build();
        stubBacklog(game);
        stubIgdbFlagsOnly("Portal 2");

        multiplayerService.derivePending(BATCH);

        assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        verify(platformService, never()).platformsOf(game.getId());
    }

    @Test
    void aConsoleGameWhoseLocalFlagIsUnknownIsNeverAskedInTheSecondSource() {
        Game game = Game.builder().title("Mario Party 6").build();
        stubBacklog(game);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Mario Party 6")).thenReturn(Optional.empty());

        multiplayerService.derivePending(BATCH);

        assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        verify(platformService, never()).platformsOf(game.getId());
    }

    @Test
    void aGapIsStoredWithItsReasonAndCountsAnAttempt() {
        Game game = aFlaggedConsoleGame("Mario Party 7");
        stubBacklog(game);
        stubIgdbFlagsOnly("Mario Party 7");
        stubSecondSource(game, List.of("GameCube"), PlayerCountLookup.gap(PlayerCountGap.ENTRIES_DISAGREE));

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getMaxLocalPlayers()).isNull();
            softly.assertThat(game.getMaxLocalPlayersSource()).isNull();
            softly.assertThat(game.getPlayerCountGap()).isEqualTo(PlayerCountGap.ENTRIES_DISAGREE);
            softly.assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        });
    }

    @Test
    void aPostponedLookupStoresNothingAndCountsNoAttempt() {
        Game game = aFlaggedConsoleGame("Mario Party 6");
        stubBacklog(game);
        stubIgdbFlagsOnly("Mario Party 6");
        stubSecondSource(game, List.of("GameCube"), PlayerCountLookup.deferred());

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(game.getMaxLocalPlayers()).isNull();
            softly.assertThat(game.getPlayerCountGap()).isNull();
            softly.assertThat(game.getMultiplayerAttempts()).isZero();
        });
    }

    @Test
    void withoutAKeyAnEligibleGameCountsAnAttemptExactlyAsBeforeTheSourceExisted() {
        Game game = aFlaggedConsoleGame("Mario Party 6");
        stubBacklog(game);
        stubIgdbFlagsOnly("Mario Party 6");
        when(theGamesDbClient.isConfigured()).thenReturn(false);

        multiplayerService.derivePending(BATCH);

        assertThat(game.getMultiplayerAttempts()).isEqualTo(1);
        verify(platformService, never()).platformsOf(game.getId());
    }

    @Test
    void aGameOnTwoConsolesNeedsBothToAgree() {
        Game agreeing = aFlaggedConsoleGame("Gauntlet - Dark Legacy");
        Game disagreeing = aFlaggedConsoleGame("Mario Party 7");
        stubBacklog(agreeing, disagreeing);
        stubIgdbFlagsOnly("Gauntlet - Dark Legacy");
        stubIgdbFlagsOnly("Mario Party 7");
        when(theGamesDbClient.isConfigured()).thenReturn(true);
        when(platformService.platformsOf(agreeing.getId())).thenReturn(List.of("GameCube", "PlayStation 2"));
        when(platformService.platformsOf(disagreeing.getId())).thenReturn(List.of("GameCube", "PlayStation 2"));
        when(theGamesDbClient.lookup("Gauntlet - Dark Legacy", agreeing.getTitleKey(), "GameCube"))
                .thenReturn(PlayerCountLookup.found(4));
        when(theGamesDbClient.lookup("Gauntlet - Dark Legacy", agreeing.getTitleKey(), "PlayStation 2"))
                .thenReturn(PlayerCountLookup.found(4));
        when(theGamesDbClient.lookup("Mario Party 7", disagreeing.getTitleKey(), "GameCube"))
                .thenReturn(PlayerCountLookup.found(4));
        when(theGamesDbClient.lookup("Mario Party 7", disagreeing.getTitleKey(), "PlayStation 2"))
                .thenReturn(PlayerCountLookup.found(6));

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(agreeing.getMaxLocalPlayers()).isEqualTo(4);
            softly.assertThat(disagreeing.getMaxLocalPlayers()).isNull();
            softly.assertThat(disagreeing.getPlayerCountGap()).isEqualTo(PlayerCountGap.ENTRIES_DISAGREE);
        });
    }

    @Test
    void aTitleOnTheSameConsoleIsLookedUpOncePerBatch() {
        Game first = aFlaggedConsoleGame("Mario Party 6");
        Game second = aFlaggedConsoleGame("Mario Party 6");
        stubBacklog(first, second);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode("Mario Party 6"))
                .thenReturn(Optional.of(new IgdbClient.MultiplayerMode(true, true, false, null)));
        when(theGamesDbClient.isConfigured()).thenReturn(true);
        when(platformService.platformsOf(first.getId())).thenReturn(List.of("GameCube"));
        when(platformService.platformsOf(second.getId())).thenReturn(List.of("GameCube"));
        when(theGamesDbClient.lookup("Mario Party 6", first.getTitleKey(), "GameCube"))
                .thenReturn(PlayerCountLookup.found(4));

        multiplayerService.derivePending(BATCH);

        assertSoftly(softly -> {
            softly.assertThat(first.getMaxLocalPlayers()).isEqualTo(4);
            softly.assertThat(second.getMaxLocalPlayers()).isEqualTo(4);
        });
        verify(theGamesDbClient, times(1)).lookup("Mario Party 6", first.getTitleKey(), "GameCube");
    }

    @Test
    void aBulkRederiveResetsAttemptsAndAsksTheSecondSourceAgain() {
        Game game = aFlaggedConsoleGame("Mario Party 6");
        game.recordMultiplayerFailure();
        game.recordMultiplayerFailure();
        game.recordMultiplayerFailure();
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        stubIgdbFlagsOnly("Mario Party 6");
        stubSecondSource(game, List.of("GameCube"), PlayerCountLookup.found(4));

        multiplayerService.rederive(game.getId());

        assertSoftly(softly -> {
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(4);
            softly.assertThat(game.getMultiplayerAttempts()).isZero();
        });
        verify(gameRepository).save(game);
    }

    @Test
    void aManualRefreshUsesTheSameSources() {
        Game game = aFlaggedConsoleGame("Mario Party 6");
        stubIgdbFlagsOnly("Mario Party 6");
        stubSecondSource(game, List.of("GameCube"), PlayerCountLookup.found(4));

        multiplayerService.refresh(game);

        assertThat(game.getMaxLocalPlayersSource()).isEqualTo(PlayerCountSource.THEGAMESDB);
    }

    private Game aFlaggedConsoleGame(String title) {
        return Game.builder().title(title).localMultiplayer(true).splitScreen(true)
                .multiplayerSource(MultiplayerSource.IGDB).build();
    }

    private void stubIgdbFlagsOnly(String title) {
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.resolveMultiplayerMode(title))
                .thenReturn(Optional.of(new IgdbClient.MultiplayerMode(true, true, false, null)));
    }

    private void stubSecondSource(Game game, List<String> consoles, PlayerCountLookup lookup) {
        when(theGamesDbClient.isConfigured()).thenReturn(true);
        when(platformService.platformsOf(game.getId())).thenReturn(consoles);
        for (String console : consoles) {
            when(theGamesDbClient.lookup(game.getTitle(), game.getTitleKey(), console)).thenReturn(lookup);
        }
    }

    private void stubBacklog(Game... games) {
        when(gameRepository.findMultiplayerBacklog(eq(MAX_ATTEMPTS), eq(FIRST_BATCH)))
                .thenReturn(List.of(games));
    }

    private Game aSteamGame(String appId, String title) {
        return Game.builder().steamAppId(appId).title(title).build();
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
