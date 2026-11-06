package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountGap;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibraryHealthRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HealthExceptionsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryHealthResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlayerCountReason;
import dev.jordy.jordylab.shared.config.JpaConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.when;

/** The health counts and exception lists against the real schema: only visible games count, and count and list agree. */
@DataJpaTest
@Testcontainers
@Import({JpaConfiguration.class, LibraryHealthService.class})
class LibraryHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private LibraryHealthService service;

    @MockitoBean
    private TheGamesDbClient theGamesDbClient;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    @Autowired
    private GameInstallationRepository installationRepository;

    @Autowired
    private GameEmbeddingRepository embeddingRepository;

    @Autowired
    private LibraryHealthRepository healthRepository;

    private ScanSource source;

    @BeforeEach
    void setUp() {
        when(theGamesDbClient.state()).thenReturn(new TheGamesDbClient.ClientState(true, null, null, 938));
        source = scanSourceRepository.save(ScanSource.builder()
                .host(hostRepository.save(Host.builder().hostname("htpc").build())).sourceType(SourceType.STEAM)
                .enabled(true).build());
    }

    private Game visibleGame(String title) {
        Game game = gameRepository.save(Game.builder().title(title).build());
        installationRepository.save(GameInstallation.builder().game(game).source(source).externalRef(title)
                .platform("Steam").firstSeenAt(NOW).lastSeenAt(NOW).build());

        return game;
    }

    @Test
    void countsAndListsWhatIsMissingFromVisibleGamesOnly() {
        Game complete = visibleGame("Complete");
        complete.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://x/c.jpg");
        complete.applyDeterministicDescription("A description.");
        gameRepository.save(complete);
        embeddingRepository.upsert(complete.getId(), "m", "h", GameEmbeddingService.vectorLiteral(new float[1536]), NOW);
        visibleGame("Bare");
        gameRepository.save(Game.builder().title("Invisible").build());

        LibraryHealthResponse health = service.health();
        HealthExceptionsResponse covers = service.exceptions(LibraryHealthService.Kind.COVER);
        HealthExceptionsResponse descriptions = service.exceptions(LibraryHealthService.Kind.DESCRIPTION);
        HealthExceptionsResponse index = service.exceptions(LibraryHealthService.Kind.INDEX);

        assertSoftly(softly -> {
            softly.assertThat(health.totalGames()).isEqualTo(2);
            softly.assertThat(health.gamesWithoutCover()).isEqualTo(1);
            softly.assertThat(health.gamesWithoutDescription()).isEqualTo(1);
            softly.assertThat(health.gamesPendingIndex()).isEqualTo(1);
            softly.assertThat(covers.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Bare");
            softly.assertThat(covers.total()).isEqualTo(1);
            softly.assertThat(descriptions.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Bare");
            softly.assertThat(index.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Bare");
            softly.assertThat(healthRepository.countVisible()).isEqualTo(2);
        });
    }

    private Game visibleSteamGame(String title, String appId) {
        Game game = gameRepository.save(Game.builder().title(title).steamAppId(appId).build());
        installationRepository.save(GameInstallation.builder().game(game).source(source).externalRef(title)
                .platform("Steam").firstSeenAt(NOW).lastSeenAt(NOW).build());

        return game;
    }

    private Game flaggedLocal(Game game) {
        game.applyDeterministicMultiplayer(true, null, null, MultiplayerSource.IGDB);

        return gameRepository.save(game);
    }

    private void aPlayerCountLibrary() {
        Game fromSecondSource = flaggedLocal(visibleGame("Mario Party 6"));
        fromSecondSource.applyPlayerCount(4, PlayerCountSource.THEGAMESDB);
        gameRepository.save(fromSecondSource);
        Game fromIgdb = flaggedLocal(visibleGame("Wave Race"));
        fromIgdb.applyFillOnlyMultiplayer(true, null, 2, MultiplayerSource.IGDB);
        gameRepository.save(fromIgdb);
        Game steamFromIgdb = flaggedLocal(visibleSteamGame("Portal 2", "620"));
        steamFromIgdb.applyFillOnlyMultiplayer(true, null, 4, MultiplayerSource.IGDB);
        gameRepository.save(steamFromIgdb);
        Game disagreeing = flaggedLocal(visibleGame("Mario Party 7"));
        disagreeing.recordPlayerCountGap(PlayerCountGap.ENTRIES_DISAGREE);
        gameRepository.save(disagreeing);
        Game unmatched = flaggedLocal(visibleGame("Gauntlet"));
        unmatched.recordPlayerCountGap(PlayerCountGap.NO_MATCH);
        gameRepository.save(unmatched);
        flaggedLocal(visibleGame("Cel Damage"));
        flaggedLocal(visibleSteamGame("Boomerang Fu", "965680"));
        visibleGame("Flag Unknown");
        flaggedLocal(gameRepository.save(Game.builder().title("Invisible Local").build()));
    }

    @Test
    void countsWhereThePlayerCountsComeFromAndWhyTheMissingOnesAreMissing() {
        aPlayerCountLibrary();

        LibraryHealthResponse.PlayerCounts counts = service.health().playerCounts();

        assertSoftly(softly -> {
            softly.assertThat(counts.localMultiplayerGames()).isEqualTo(7);
            softly.assertThat(counts.filled()).containsEntry(PlayerCountSource.IGDB, 2L)
                    .containsEntry(PlayerCountSource.THEGAMESDB, 1L);
            softly.assertThat(counts.unknown()).isEqualTo(4);
            softly.assertThat(counts.flagUnknown()).isEqualTo(1);
            softly.assertThat(counts.unknownByReason()).containsEntry(PlayerCountReason.STEAM_NO_SOURCE, 1L)
                    .containsEntry(PlayerCountReason.ENTRIES_DISAGREE, 1L)
                    .containsEntry(PlayerCountReason.NO_MATCH, 1L)
                    .containsEntry(PlayerCountReason.COUNT_BELOW_TWO, 0L)
                    .containsEntry(PlayerCountReason.CONSOLE_NOT_LISTED, 0L)
                    .containsEntry(PlayerCountReason.NOT_LOOKED_UP_YET, 1L);
            softly.assertThat(counts.unknown() + counts.filled().values().stream().mapToLong(Long::longValue).sum())
                    .isEqualTo(counts.localMultiplayerGames());
        });
    }

    @Test
    void theSecondSourcesStateComesFromTheClient() {
        Instant pausedUntil = Instant.parse("2026-11-02T08:00:00Z");
        when(theGamesDbClient.state()).thenReturn(new TheGamesDbClient.ClientState(true, pausedUntil,
                TheGamesDbClient.PauseReason.ALLOWANCE_LOW, 18));

        LibraryHealthResponse.SourceState state = service.health().playerCounts().theGamesDb();

        assertSoftly(softly -> {
            softly.assertThat(state.configured()).isTrue();
            softly.assertThat(state.pausedUntil()).isEqualTo(pausedUntil);
            softly.assertThat(state.pauseReason()).isEqualTo("ALLOWANCE_LOW");
            softly.assertThat(state.remainingAllowance()).isEqualTo(18);
        });
    }

    @Test
    void thePlayerCountListsHoldTheSameGamesAsTheNumbersAndEachCarriesItsReason() {
        aPlayerCountLibrary();

        HealthExceptionsResponse all = service.exceptions(LibraryHealthService.Kind.PLAYER_COUNT);
        HealthExceptionsResponse disagreeing = service.exceptions(LibraryHealthService.Kind.PLAYER_COUNT,
                PlayerCountReason.ENTRIES_DISAGREE);
        HealthExceptionsResponse steam = service.exceptions(LibraryHealthService.Kind.PLAYER_COUNT,
                PlayerCountReason.STEAM_NO_SOURCE);

        assertSoftly(softly -> {
            softly.assertThat(all.total()).isEqualTo(4);
            softly.assertThat(all.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactlyInAnyOrder("Boomerang Fu", "Mario Party 7", "Gauntlet", "Cel Damage");
            softly.assertThat(all.games()).extracting(HealthExceptionsResponse.HealthException::reason)
                    .containsExactlyInAnyOrder(PlayerCountReason.STEAM_NO_SOURCE, PlayerCountReason.ENTRIES_DISAGREE,
                            PlayerCountReason.NO_MATCH, PlayerCountReason.NOT_LOOKED_UP_YET);
            softly.assertThat(disagreeing.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Mario Party 7");
            softly.assertThat(steam.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Boomerang Fu");
            softly.assertThat(Map.of(PlayerCountReason.ENTRIES_DISAGREE, disagreeing.total())).containsEntry(
                    PlayerCountReason.ENTRIES_DISAGREE, service.health().playerCounts().unknownByReason()
                            .get(PlayerCountReason.ENTRIES_DISAGREE));
        });
    }

    @Test
    void theOtherKindsCarryNoReasonAndIgnoreOne() {
        visibleGame("Bare");

        HealthExceptionsResponse covers = service.exceptions(LibraryHealthService.Kind.COVER,
                PlayerCountReason.NO_MATCH);

        assertSoftly(softly -> {
            softly.assertThat(covers.games()).hasSize(1);
            softly.assertThat(covers.games().getFirst().reason()).isNull();
        });
    }
}
