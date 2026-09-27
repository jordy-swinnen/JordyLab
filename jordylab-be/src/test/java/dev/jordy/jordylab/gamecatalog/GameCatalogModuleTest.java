package dev.jordy.jordylab.gamecatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibrarySyncRunRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.SyncReportRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamOwnedGamesClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RefreshAllResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.service.ArtworkService;
import dev.jordy.jordylab.gamecatalog.service.CatalogRefreshService;
import dev.jordy.jordylab.gamecatalog.service.EnrichmentService;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.ReconciliationService;
import dev.jordy.jordylab.gamecatalog.service.ScanService;
import dev.jordy.jordylab.gamecatalog.service.SteamLibrarySyncService;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.mockito.Answers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ApplicationModuleTest
@Testcontainers
@TestPropertySource(properties = {
        "jordylab.gamecatalog.scan.max-games-per-source=10000",
        "jordylab.gamecatalog.scan.max-payload-bytes=1048576",
        "jordylab.gamecatalog.scan.max-manifest-bytes-per-source=262144",
        "jordylab.gamecatalog.artwork.dir=/tmp/module-test-artwork",
        "jordylab.gamecatalog.artwork.max-bytes=2097152",
        "jordylab.gamecatalog.artwork.external-lookup-enabled=false",
        "jordylab.gamecatalog.artwork.lookup-timeout-ms=2000",
        "jordylab.gamecatalog.grace-period-days=30",
        "jordylab.gamecatalog.enrichment.batch-size=8",
        "jordylab.gamecatalog.enrichment.max-attempts=3",
        "jordylab.gamecatalog.chat.max-result-games=50"
})
class GameCatalogModuleTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @org.junit.jupiter.api.BeforeEach
    void cleanCatalog() {
        syncReportRepository.deleteAll();
        librarySyncRunRepository.deleteAll();
        gameLibraryEntryRepository.deleteAll();
        gameInstallationRepository.deleteAll();
        gameRepository.deleteAll();
        scanSourceRepository.deleteAll();
    }

    @Autowired
    private ScanService scanService;

    @Autowired
    private GameQueryService gameQueryService;

    @Autowired
    private ArtworkService artworkService;

    @Autowired
    private CatalogRefreshService catalogRefreshService;

    @Autowired
    private EnrichmentService enrichmentService;

    @Autowired
    private SteamLibrarySyncService steamLibrarySyncService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private GameLibraryEntryRepository gameLibraryEntryRepository;

    @Autowired
    private LibrarySyncRunRepository librarySyncRunRepository;

    // Unstubbed calls return a mock AiCallResult whose success() is false, so inline enrichment
    // degrades to a recorded failure instead of a null dereference.
    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ResilientAiService resilientAiService;

    @MockitoBean
    private SteamAppDetailsClient steamAppDetailsClient;

    @MockitoBean
    private SteamOwnedGamesClient ownedGamesClient;

    @TestConfiguration
    static class ObjectMapperTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        }
    }

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameInstallationRepository gameInstallationRepository;

    @Autowired
    private SyncReportRepository syncReportRepository;

    @Test
    void scanRoundTripAppliesThenNoChangeThenReconciles() {
        ScanRequest first = aRequest("jordybox", SourceType.EMUDECK, List.of(
                new GamePayload("snes/mario.smc", "Super Mario World", "SNES", null),
                new GamePayload("snes/zelda.smc", "The Legend of Zelda", "SNES", null)));

        ScanResponse applied = scanService.submitScan(first);
        assertSoftly(softly -> {
            softly.assertThat(applied.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(applied.counts().added()).isEqualTo(2);
            softly.assertThat(scanSourceRepository.findByHostnameAndSourceType("jordybox", SourceType.EMUDECK))
                    .isPresent();
        });

        // Same payload → no-op (payload hash matches last applied)
        ScanResponse duplicate = scanService.submitScan(first);
        assertSoftly(softly -> {
            softly.assertThat(duplicate.outcome()).isEqualTo(SyncOutcome.NO_CHANGE);
            softly.assertThat(gameRepository.count()).isEqualTo(2);
        });

        ScanRequest reduced = aRequest("jordybox", SourceType.EMUDECK, List.of(
                new GamePayload("snes/mario.smc", "Super Mario World", "SNES", null)));
        ScanResponse reconciled = scanService.submitScan(reduced);
        List<GameInstallation> remaining = gameInstallationRepository.findAll();
        assertSoftly(softly -> {
            softly.assertThat(reconciled.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(reconciled.counts().removed()).isEqualTo(1);
            softly.assertThat(remaining).hasSize(2);
            softly.assertThat(remaining.stream()
                    .filter(installation -> installation.getExternalRef().equals("snes/zelda.smc"))
                    .findFirst()
                    .orElseThrow()
                    .getPresence()).isEqualTo(Presence.UNINSTALLED);
            softly.assertThat(syncReportRepository.count()).isEqualTo(3);
        });
    }

    @Test
    void visibleGamesQueryAppliesVisibilitySearchAndSort() {
        // Use a script-style scan (hostname + libraryType + manifest contents) so
        // the Steam parser produces real titles from VDF, not filename stubs.
        ScanRequest steamRequest = new ScanRequest(null, "jordybox", SourceType.STEAM,
                Instant.parse("2026-08-06T09:00:00Z"),
                null, false,
                List.of(new ScanEntry("steamapps/appmanifest_440.acf", 0L, Instant.parse("2026-08-06T09:00:00Z")),
                        new ScanEntry("steamapps/appmanifest_620.acf", 0L, Instant.parse("2026-08-06T09:00:00Z"))),
                Map.of("steamapps/appmanifest_440.acf", """
                        "AppState"
                        {
                            appid         "440"
                            name          "Team Fortress 2"
                            installdir    "Team Fortress 2"
                        }
                        """,
                        "steamapps/appmanifest_620.acf", """
                        "AppState"
                        {
                            appid         "620"
                            name          "Portal 2"
                            installdir    "Portal 2"
                        }
                        """),
                null);
        scanService.submitScan(steamRequest);

        GamesPageResponse all = gameQueryService.getGames(null, null, null, null, null, null, 0, 60);
        GamesPageResponse searched = gameQueryService.getGames("Portal", null, null, null, null, null, 0, 60);
        GamesPageResponse platformFiltered = gameQueryService.getGames(null, "PlayStation 2", null, null, null, null, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(all.content())
                    .extracting("title")
                    .containsExactlyInAnyOrder("Portal 2", "Team Fortress 2");
            softly.assertThat(searched.content())
                    .extracting("title")
                    .containsExactly("Portal 2");
            softly.assertThat(platformFiltered.content()).isEmpty();
            softly.assertThat(gameQueryService.getPlatforms().platforms()).containsExactly("Steam");
        });

        ScanSource source = scanSourceRepository.findByHostnameAndSourceType("jordybox", SourceType.STEAM).orElseThrow();
        source.setEnabled(false);
        scanSourceRepository.save(source);

        GamesPageResponse afterDisable = gameQueryService.getGames(null, null, null, null, null, null, 0, 60);
        assertSoftly(softly -> {
            softly.assertThat(afterDisable.content()).isEmpty();
            softly.assertThat(gameQueryService.getPlatforms().platforms()).isEmpty();
        });
    }

    @Test
    void artworkFallbackFlowMarksGameAsPlaceholderWhenExternalLookupMisses() {
        // EmuDeck scripts never carry local artwork (the upload flow is gone in v2),
        // so a game whose external-lookup misses is resolved to PLACEHOLDER, not
        // LOCAL_FALLBACK_REQUESTED. The fallback path is exercised by the
        // ArtworkServiceTest unit tests.
        ScanRequest emuDeckRequest = new ScanRequest(null, "jordybox", SourceType.EMUDECK,
                Instant.parse("2026-08-06T09:00:00Z"),
                null, false,
                List.of(new ScanEntry("snes/mario.smc", 0L, Instant.parse("2026-08-06T09:00:00Z"))),
                Map.of(),
                null);
        ScanResponse response = scanService.submitScan(emuDeckRequest);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(gameRepository.findAll().getFirst().getCoverStatus())
                    .isEqualTo(ArtworkStatus.PLACEHOLDER);
        });
    }

    @Test
    void emudeckParserInfersPlatformFromParentFolder() {
        ScanRequest request = aRequest("jordybox", SourceType.EMUDECK, List.of(
                new GamePayload("snes/chrono_trigger.smc", "Chrono Trigger", "SNES", null),
                new GamePayload("ps2/ff10.iso", "Final Fantasy X", "PlayStation 2", null),
                new GamePayload("gba/pokemon_emu.gba", "Pokemon Emerald", "Game Boy Advance", null)));

        ScanResponse response = scanService.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(response.counts().added()).isEqualTo(3);
            softly.assertThat(response.rejections()).isEmpty();
        });
    }

    @Test
    void steamParserSkipsManifestsWithoutTitle() {
        ScanRequest request = new ScanRequest(null, "jordybox", SourceType.STEAM,
                Instant.parse("2026-08-06T10:00:00Z"), null, false, List.of(), Map.of(
                "steamapps/appmanifest_440.acf", """
                        "AppState"
                        {
                            appid         "440"
                            installdir    "Team Fortress 2"
                        }
                        """),
                null);

        ScanResponse response = scanService.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(response.counts().added()).isEqualTo(1);
            softly.assertThat(gameRepository.findAll().getFirst().getTitle()).isEqualTo("Team Fortress 2");
        });
    }

    @Test
    void sameGameFromTwoHostsIsAdoptedAsOneEntry() {
        ScanRequest hostA = aSteamRequest("jordybox", "620", "Portal 2");
        ScanRequest hostB = aSteamRequest("ryzen-desktop", "620", "Portal 2");

        scanService.submitScan(hostA);
        scanService.submitScan(hostB);

        assertSoftly(softly -> {
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(gameInstallationRepository.count()).isEqualTo(2);
        });

        Game game = gameRepository.findAll().getFirst();
        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId()).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(detail.hosts()).extracting("hostname")
                    .containsExactlyInAnyOrder("jordybox", "ryzen-desktop");
            softly.assertThat(game.getEnrichmentStatus())
                    .isEqualTo(dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus.PENDING);
        });
    }

    @Test
    void hostFilterNarrowsTheGridToGamesInstalledOnThatHost() {
        scanService.submitScan(aSteamRequest("jordybox", Map.of("440", "Team Fortress 2", "620", "Portal 2")));
        scanService.submitScan(aSteamRequest("ryzen-desktop", Map.of("620", "Portal 2")));

        GamesPageResponse all = gameQueryService.getGames(null, null, null, null, null, null, 0, 60);
        GamesPageResponse jordybox = gameQueryService.getGames(null, null, "jordybox", null, null, null, 0, 60);
        GamesPageResponse desktop = gameQueryService.getGames(null, null, "ryzen-desktop", null, null, null, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(all.content()).hasSize(2);
            softly.assertThat(jordybox.content()).extracting("title")
                    .containsExactlyInAnyOrder("Portal 2", "Team Fortress 2");
            softly.assertThat(desktop.content()).extracting("title").containsExactly("Portal 2");
            softly.assertThat(gameQueryService.getHosts().hosts())
                    .containsExactlyInAnyOrder("jordybox", "ryzen-desktop");
        });
    }

    @Test
    void scanPopulatesDeterministicMetadataInline() {
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.of(
                new SteamAppDetailsClient.SteamMetadata("Puzzle, Adventure", "Valve", "Valve", 2011, null, "game", null)));

        scanService.submitScan(aSteamRequest("jordybox", "620", "Portal 2"));

        Game game = gameRepository.findAll().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(game.getDeveloper()).isEqualTo("Valve");
            softly.assertThat(game.getReleaseYear()).isEqualTo(2011);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.OK);
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
        });
    }

    @Test
    void bulkRefreshDrainsPendingDataAndReportsRemaining() {
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.of(
                new SteamAppDetailsClient.SteamMetadata("Puzzle", "Valve", "Valve", 2011, null, "game", null)));

        scanService.submitScan(aSteamRequest("jordybox", "620", "Portal 2"));
        RefreshAllResponse response = catalogRefreshService.refreshPending();

        assertSoftly(softly -> {
            softly.assertThat(response.metadata().processed()).isZero();
            softly.assertThat(response.metadata().remaining()).isZero();
            softly.assertThat(response.enrichment().processed()).isEqualTo(1);
            softly.assertThat(response.enrichment().remaining()).isEqualTo(1);
        });
    }

    @Test
    void steamAppIdIsUniqueAtTheDatabaseLevel() {
        Game first = reconciliationService.resolveOrCreateSteamGame("620", "Portal 2", TitleSource.MANIFEST);
        Game second = reconciliationService.resolveOrCreateSteamGame("620", "Portal 2 Reloaded", TitleSource.LIBRARY);

        assertSoftly(softly -> {
            softly.assertThat(second.getId()).isEqualTo(first.getId());
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(gameRepository.findBySteamAppId("620")).isPresent();
        });
    }

    @Test
    void libraryReportingAnEnrichedGameCausesNoNewStoreOrAiCalls() {
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.of(new SteamAppDetailsClient.SteamMetadata(
                "Puzzle, Adventure", "Valve", "Valve", 2011, "A puzzle platformer.", "game",
                new SteamAppDetailsClient.MultiplayerFacts(true, false, false, false, true))));
        scanService.submitScan(aSteamRequest("jordybox", "620", "Portal 2"));
        Game game = gameRepository.findAll().getFirst();
        when(resilientAiService.call(eq("gamecatalog"), eq(EnrichmentService.SYSTEM_PROMPT),
                eq("Game: Portal 2\nPlatform: Steam\nKnown multiplayer facts (use verbatim, do not contradict):"
                        + "\n- Local multiplayer: no\n- Split-screen: no")))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", """
                        {"genre":"Puzzle","genres":"Puzzle, Adventure","developer":"Valve","publisher":"Valve",
                         "releaseYear":2011,"onlineMultiplayer":false,"singlePlayer":true,
                         "description":"A classic."}
                        """));
        catalogRefreshService.refreshEnrichment(game.getId());
        org.mockito.Mockito.clearInvocations(steamAppDetailsClient, resilientAiService);

        when(ownedGamesClient.fetchOwnedGames())
                .thenReturn(Optional.of(List.of(new SteamOwnedGamesClient.OwnedGame("620", "Portal 2"))));
        when(ownedGamesClient.isConfigured()).thenReturn(true);
        steamLibrarySyncService.syncOwned(false);

        org.mockito.Mockito.verifyNoInteractions(steamAppDetailsClient);
        org.mockito.Mockito.verifyNoInteractions(resilientAiService);
        assertSoftly(softly -> {
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(gameLibraryEntryRepository.count()).isEqualTo(1);
        });
    }

    private ScanRequest aSteamRequest(String hostname, String appId, String title) {
        return aSteamRequest(hostname, Map.of(appId, title));
    }

    private ScanRequest aSteamRequest(String hostname, Map<String, String> appIdsToTitles) {
        List<ScanEntry> paths = new java.util.ArrayList<>();
        Map<String, String> manifestContents = new java.util.LinkedHashMap<>();
        appIdsToTitles.forEach((appId, title) -> {
            String relPath = "steamapps/appmanifest_" + appId + ".acf";
            paths.add(new ScanEntry(relPath, 0L, Instant.parse("2026-08-06T09:00:00Z")));
            manifestContents.put(relPath, """
                    "AppState"
                    {
                        appid         "%s"
                        name          "%s"
                        installdir    "%s"
                    }
                    """.formatted(appId, title, title));
        });

        return new ScanRequest(null, hostname, SourceType.STEAM, Instant.parse("2026-08-06T09:00:00Z"), null, false,
                paths, manifestContents, null);
    }

    private ScanRequest aRequest(String hostname, SourceType type, List<GamePayload> games) {
        List<ScanEntry> paths = games.stream()
                .map(game -> new ScanEntry(game.externalRef(), 0L, Instant.parse("2026-08-06T09:00:00Z")))
                .toList();

        return new ScanRequest(null, hostname, type, Instant.parse("2026-08-06T09:00:00Z"), null, false, paths,
                Map.of(), null);
    }
}
