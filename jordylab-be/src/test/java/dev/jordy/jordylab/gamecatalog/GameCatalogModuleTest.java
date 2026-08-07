package dev.jordy.jordylab.gamecatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.SyncReportRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.service.ArtworkService;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.ScanService;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
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

import static org.assertj.core.api.SoftAssertions.assertSoftly;

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
        "jordylab.gamecatalog.enrichment.batch-size=50",
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
        gameRepository.deleteAll();
        scanSourceRepository.deleteAll();
    }

    @Autowired
    private ScanService scanService;

    @Autowired
    private GameQueryService gameQueryService;

    @Autowired
    private ArtworkService artworkService;

    @MockitoBean
    private ResilientAiService resilientAiService;

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
        List<Game> remaining = gameRepository.findAll();
        assertSoftly(softly -> {
            softly.assertThat(reconciled.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(reconciled.counts().removed()).isEqualTo(1);
            softly.assertThat(remaining).hasSize(2);
            softly.assertThat(remaining.stream()
                    .filter(game -> game.getExternalRef().equals("snes/zelda.smc"))
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
        ScanRequest steamRequest = new ScanRequest("jordybox", SourceType.STEAM,
                Instant.parse("2026-08-06T09:00:00Z"),
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
                        """));
        scanService.submitScan(steamRequest);

        GamesPageResponse all = gameQueryService.getGames(null, null, 0, 60);
        GamesPageResponse searched = gameQueryService.getGames("Portal", null, 0, 60);
        GamesPageResponse platformFiltered = gameQueryService.getGames(null, "PlayStation 2", 0, 60);

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

        var source = scanSourceRepository.findByHostnameAndSourceType("jordybox", SourceType.STEAM).orElseThrow();
        source.setEnabled(false);
        scanSourceRepository.save(source);

        GamesPageResponse afterDisable = gameQueryService.getGames(null, null, 0, 60);
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
        ScanRequest emuDeckRequest = new ScanRequest("jordybox", SourceType.EMUDECK,
                Instant.parse("2026-08-06T09:00:00Z"),
                List.of(new ScanEntry("snes/mario.smc", 0L, Instant.parse("2026-08-06T09:00:00Z"))),
                Map.of());
        ScanResponse response = scanService.submitScan(emuDeckRequest);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(gameRepository.findAll().getFirst().getArtworkStatus())
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
        ScanRequest request = new ScanRequest("jordybox", SourceType.STEAM,
                Instant.parse("2026-08-06T10:00:00Z"), List.of(), Map.of(
                "steamapps/appmanifest_440.acf", """
                        "AppState"
                        {
                            appid         "440"
                            installdir    "Team Fortress 2"
                        }
                        """));

        ScanResponse response = scanService.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(response.counts().added()).isEqualTo(1);
            softly.assertThat(gameRepository.findAll().getFirst().getTitle()).isEqualTo("Team Fortress 2");
        });
    }

    private ScanRequest aRequest(String hostname, SourceType type, List<GamePayload> games) {
        List<ScanEntry> paths = games.stream()
                .map(game -> new ScanEntry(game.externalRef(), 0L, Instant.parse("2026-08-06T09:00:00Z")))
                .toList();

        return new ScanRequest(hostname, type, Instant.parse("2026-08-06T09:00:00Z"), paths, Map.of());
    }
}
