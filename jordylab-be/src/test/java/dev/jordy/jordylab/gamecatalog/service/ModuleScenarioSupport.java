package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibrarySyncRunRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.SyncReportRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamOwnedGamesClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.service.autofill.AutoFillTrigger;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Shared set-up for the scenario tests that run the real catalog services against PostgreSQL (the whole module with
 * only the outside world mocked: AI, the Steam store and the Steam Web API). Every test starts from an empty catalog, and
 * the background auto-fill is switched off so a test decides when (if ever) it runs.
 */
/*
 * Each subclass gets its own container (the static one below is started and stopped per test class), so each must also get its
 * own Spring context: a cached context would keep talking to the previous, stopped container.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ApplicationModuleTest
@Testcontainers
@Import(ModuleScenarioSupport.ScenarioConfiguration.class)
@TestPropertySource(properties = {
        "jordylab.gamecatalog.scan.max-games-per-source=50000",
        "jordylab.gamecatalog.scan.max-payload-bytes=8388608",
        "jordylab.gamecatalog.scan.max-manifest-bytes-per-source=262144",
        "jordylab.gamecatalog.artwork.dir=/tmp/module-test-artwork",
        "jordylab.gamecatalog.artwork.external-lookup-enabled=false",
        "jordylab.gamecatalog.grace-period-days=30"
})
abstract class ModuleScenarioSupport {

    protected static final Instant SEEN_AT = Instant.parse("2026-08-06T09:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @TestConfiguration
    static class ScenarioConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    protected ResilientAiService resilientAiService;

    @MockitoBean
    protected SteamAppDetailsClient steamAppDetailsClient;

    @MockitoBean
    protected SteamOwnedGamesClient ownedGamesClient;

    @MockitoBean
    protected AutoFillTrigger autoFillTrigger;

    @Autowired
    protected org.springframework.transaction.support.TransactionOperations transactions;

    @Autowired
    protected ScanService scanService;

    @Autowired
    protected GameQueryService gameQueryService;

    @Autowired
    protected GameIdentityService gameIdentityService;

    @Autowired
    protected ReconciliationService reconciliationService;

    @Autowired
    protected ScanSourceService scanSourceService;

    @Autowired
    protected GameRepository gameRepository;

    @Autowired
    protected GameInstallationRepository gameInstallationRepository;

    @Autowired
    protected GameLibraryEntryRepository gameLibraryEntryRepository;

    @Autowired
    protected ConsoleGameEntryRepository consoleGameEntryRepository;

    @Autowired
    protected ConsoleRepository consoleRepository;

    @Autowired
    protected GameMarkRepository gameMarkRepository;

    @Autowired
    protected GameEmbeddingRepository gameEmbeddingRepository;

    @Autowired
    protected ScanSourceRepository scanSourceRepository;

    @Autowired
    protected HostRepository hostRepository;

    @Autowired
    protected SyncReportRepository syncReportRepository;

    @Autowired
    protected LibrarySyncRunRepository librarySyncRunRepository;

    @BeforeEach
    void startFromAnEmptyCatalog() {
        syncReportRepository.deleteAll();
        librarySyncRunRepository.deleteAll();
        gameMarkRepository.deleteAll();
        gameLibraryEntryRepository.deleteAll();
        consoleGameEntryRepository.deleteAll();
        consoleRepository.deleteAll();
        gameInstallationRepository.deleteAll();
        gameRepository.deleteAll();
        scanSourceRepository.deleteAll();
        hostRepository.deleteAll();
    }

    protected ScanRequest emuDeckScan(String hostname, String... titles) {
        return emuDeckScan(hostname, false, titles);
    }

    /** A scan that may remove everything the host reported before: needs {@code force}, the shrink guard says so. */
    protected ScanRequest emuDeckScan(String hostname, boolean force, String... titles) {
        List<GamePayload> games = java.util.Arrays.stream(titles)
                .map(title -> new GamePayload("snes/" + title + ".smc", title, "SNES", null)).toList();
        List<ScanEntry> paths = games.stream().map(game -> new ScanEntry(game.externalRef(), 0L, SEEN_AT)).toList();

        return new ScanRequest(null, hostname, SourceType.EMUDECK, SEEN_AT, null, force, paths, Map.of(), null);
    }

    protected ScanRequest steamScan(String hostname, String appId, String title) {
        String path = "steamapps/appmanifest_" + appId + ".acf";

        return new ScanRequest(null, hostname, SourceType.STEAM, SEEN_AT, null, false,
                List.of(new ScanEntry(path, 0L, SEEN_AT)),
                Map.of(path, """
                        "AppState"
                        {
                            appid         "%s"
                            name          "%s"
                            installdir    "%s"
                        }
                        """.formatted(appId, title, title)), null);
    }

    protected Game onlyGame() {
        return gameRepository.findAll().getFirst();
    }
}
