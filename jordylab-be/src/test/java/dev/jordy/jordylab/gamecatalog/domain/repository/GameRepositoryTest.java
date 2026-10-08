package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.shared.config.JpaConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The visibility rule and the grid filters against real PostgreSQL and the real Flyway schema (spec 013): a game is
 * visible through an installed copy on an enabled source, an active Steam library entry, or a console entry, and
 * through nothing else. Every JPA mapping is also validated against the migrations by this context starting.
 */
@DataJpaTest
@Testcontainers
@Import(JpaConfiguration.class)
class GameRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameInstallationRepository installationRepository;

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private ConsoleRepository consoleRepository;

    @Autowired
    private ConsoleGameEntryRepository consoleGameEntryRepository;

    @Autowired
    private GameLibraryEntryRepository libraryEntryRepository;

    @Test
    void anInstalledCopyOnAnEnabledSourceMakesAGameVisibleAndAvailable() {
        Game game = game("Super Mario World");
        installCopy(game, source(host("htpc", null), SourceType.EMUDECK, true), "SNES", "smw.smc");

        assertSoftly(softly -> {
            softly.assertThat(visibleIds("INSTALLED")).contains(game.getId());
            softly.assertThat(visibleIds("NOT_INSTALLED")).doesNotContain(game.getId());
            softly.assertThat(gameRepository.findVisibleById(game.getId())).isPresent();
        });
    }

    @Test
    void disablingTheOnlySourceHidesTheGameAndNothingIsDeleted() {
        Game game = game("Super Mario World");
        ScanSource source = source(host("htpc", null), SourceType.EMUDECK, true);
        installCopy(game, source, "SNES", "smw.smc");
        source.setEnabled(false);
        scanSourceRepository.saveAndFlush(source);

        assertSoftly(softly -> {
            softly.assertThat(visibleIds("ALL")).doesNotContain(game.getId());
            softly.assertThat(gameRepository.findVisibleById(game.getId())).isEmpty();
            softly.assertThat(gameRepository.findById(game.getId())).isPresent();
        });
    }

    @Test
    void aGameOnTwoSourcesStaysVisibleWhenOneIsDisabled() {
        Game game = game("Hades");
        ScanSource first = source(host("htpc", null), SourceType.STEAM, true);
        ScanSource second = source(host("macbook", null), SourceType.STEAM, true);
        installCopy(game, first, "Steam", "1145360");
        installCopy(game, second, "Steam", "1145360-b");
        first.setEnabled(false);
        scanSourceRepository.saveAndFlush(first);

        assertSoftly(softly -> {
            softly.assertThat(visibleIds("INSTALLED")).contains(game.getId());
        });
    }

    @Test
    void anActiveLibraryEntryMakesAGameVisibleButNotInstalled() {
        Game game = game("Portal 2");
        libraryEntry(game, LibrarySource.OWNED, true);

        assertSoftly(softly -> {
            softly.assertThat(visibleIds("NOT_INSTALLED")).contains(game.getId());
            softly.assertThat(visibleIds("INSTALLED")).doesNotContain(game.getId());
        });
    }

    @Test
    void aRemovedLibraryEntryDoesNotMakeAGameVisible() {
        Game game = game("Portal 2");
        libraryEntry(game, LibrarySource.OWNED, false);

        assertSoftly(softly -> {
            softly.assertThat(visibleIds("ALL")).doesNotContain(game.getId());
            softly.assertThat(gameRepository.findVisibleById(game.getId())).isEmpty();
        });
    }

    @Test
    void aConsoleEntryMakesAGameVisibleAndAvailable() {
        Game game = game("Mario Kart 8 Deluxe");
        consoleEntry(game, console("Nintendo Switch", "Nintendo Switch"));

        assertSoftly(softly -> {
            softly.assertThat(visibleIds("INSTALLED")).contains(game.getId());
            softly.assertThat(visibleIds("NOT_INSTALLED")).doesNotContain(game.getId());
            softly.assertThat(gameRepository.findVisibleById(game.getId())).isPresent();
        });
    }

    @Test
    void aGameWithNoPlaceIsNotVisible() {
        Game game = game("Orphan");

        assertSoftly(softly -> {
            softly.assertThat(visibleIds("ALL")).doesNotContain(game.getId());
            softly.assertThat(gameRepository.findVisibleById(game.getId())).isEmpty();
        });
    }

    @Test
    void theSamePlatformFilterFindsAGameThroughAnyOfItsPlaces() {
        Game game = game("Hades");
        installCopy(game, source(host("htpc", null), SourceType.EMUDECK, true), "SNES", "hades.smc");
        consoleEntry(game, console("Nintendo Switch", "Nintendo Switch"));
        libraryEntry(game, LibrarySource.OWNED, true);

        assertSoftly(softly -> {
            softly.assertThat(idsOnPlatform("SNES")).contains(game.getId());
            softly.assertThat(idsOnPlatform("Nintendo Switch")).contains(game.getId());
            softly.assertThat(idsOnPlatform("Steam")).contains(game.getId());
            softly.assertThat(idsOnPlatform("PlayStation 5")).doesNotContain(game.getId());
        });
    }

    @Test
    void platformsComeFromThePlacesOfVisibleGames() {
        Game rom = game("Super Mario World");
        installCopy(rom, source(host("htpc", null), SourceType.EMUDECK, true), "SNES", "smw.smc");
        consoleEntry(game("Mario Kart 8 Deluxe"), console("Nintendo Switch", "Nintendo Switch"));
        libraryEntry(game("Portal 2"), LibrarySource.OWNED, true);
        Game hidden = game("Hidden ROM");
        ScanSource disabled = source(host("old-box", null), SourceType.EMUDECK, false);
        installCopy(hidden, disabled, "Dreamcast", "hidden.cdi");

        assertSoftly(softly -> {
            softly.assertThat(gameRepository.findVisiblePlatforms()).contains("SNES", "Nintendo Switch", "Steam");
            softly.assertThat(gameRepository.findVisiblePlatforms()).doesNotContain("Dreamcast");
        });
    }

    @Test
    void turningASourceOffHidesOnlyTheGamesThatLiveNowhereElse() {
        ScanSource htpc = source(host("htpc", null), SourceType.STEAM, true);
        ScanSource macbook = source(host("macbook", null), SourceType.STEAM, true);
        Game onlyHere = game("Only on the HTPC");
        installCopy(onlyHere, htpc, "Steam", "1");
        Game alsoOnMacbook = game("On both machines");
        installCopy(alsoOnMacbook, htpc, "Steam", "2");
        installCopy(alsoOnMacbook, macbook, "Steam", "2-b");
        Game inSteamLibrary = game("Also in the Steam library");
        installCopy(inSteamLibrary, htpc, "Steam", "3");
        libraryEntry(inSteamLibrary, LibrarySource.OWNED, true);
        Game onAConsole = game("Also on a console");
        installCopy(onAConsole, htpc, "Steam", "4");
        consoleEntry(onAConsole, console("Nintendo Switch", "Nintendo Switch"));
        Game elsewhereOnly = game("Only on the MacBook");
        installCopy(elsewhereOnly, macbook, "Steam", "5");

        assertSoftly(softly -> {
            softly.assertThat(gameRepository.countInstalledOnSource(htpc.getId())).isEqualTo(4);
            softly.assertThat(gameRepository.countHiddenIfSourceDisabled(htpc.getId())).isEqualTo(1);
            softly.assertThat(gameRepository.countHiddenIfSourceDisabled(macbook.getId())).isEqualTo(1);
        });
    }

    @Test
    void theSourceFilterUnderstandsTheFourLabels() {
        Game owned = game("Owned game");
        libraryEntry(owned, LibrarySource.OWNED, true);
        Game family = game("Family game");
        libraryEntry(family, LibrarySource.FAMILY, true);
        Game emulated = game("Emulated game");
        installCopy(emulated, source(host("htpc", null), SourceType.EMUDECK, true), "SNES", "e.smc");
        Game console = game("Console game");
        consoleEntry(console, console("Nintendo Switch", "Nintendo Switch"));
        Game installedSteamWithoutLibrary = game("Unsynced Steam game");
        installCopy(installedSteamWithoutLibrary, source(host("pc", null), SourceType.STEAM, true), "Steam", "9999");

        assertSoftly(softly -> {
            softly.assertThat(idsWithSource("STEAM_OWNED")).contains(owned.getId(), installedSteamWithoutLibrary.getId())
                    .doesNotContain(family.getId(), emulated.getId(), console.getId());
            softly.assertThat(idsWithSource("STEAM_FAMILY")).contains(family.getId()).doesNotContain(owned.getId());
            softly.assertThat(idsWithSource("EMULATED")).containsExactly(emulated.getId());
            softly.assertThat(idsWithSource("CONSOLE")).containsExactly(console.getId());
        });
    }

    // ------------------------------------------------------------------ helpers

    private List<java.util.UUID> visibleIds(String installStatus) {
        return gameRepository.findFiltered(GameFilter.builder().installStatus(installStatus).build(), FIRST_PAGE)
                .getContent().stream().map(Game::getId).toList();
    }

    private List<java.util.UUID> idsOnPlatform(String platform) {
        return gameRepository.findFiltered(GameFilter.builder().installStatus("ALL").platforms(List.of(platform)).build(),
                FIRST_PAGE).getContent().stream().map(Game::getId).toList();
    }

    private List<java.util.UUID> idsWithSource(String source) {
        return gameRepository.findFiltered(GameFilter.builder().installStatus("ALL")
                .sources(List.of(GameSource.valueOf(source))).build(), FIRST_PAGE).getContent()
                .stream().map(Game::getId).toList();
    }

    private Game game(String title) {
        return gameRepository.save(Game.builder().title(title).build());
    }

    private Host host(String hostname, String displayName) {
        return hostRepository.save(Host.builder().hostname(hostname).displayName(displayName).build());
    }

    private ScanSource source(Host host, SourceType type, boolean enabled) {
        return scanSourceRepository.save(ScanSource.builder().host(host).sourceType(type).enabled(enabled).build());
    }

    private void installCopy(Game game, ScanSource source, String platform, String externalRef) {
        installationRepository.save(GameInstallation.builder().game(game).source(source).externalRef(externalRef)
                .platform(platform).firstSeenAt(NOW).lastSeenAt(NOW).build());
    }

    private void libraryEntry(Game game, LibrarySource librarySource, boolean active) {
        libraryEntryRepository.save(GameLibraryEntry.builder().game(game).librarySource(librarySource)
                .firstSeenAt(NOW).lastSeenAt(NOW).removedAt(active ? null : NOW).build());
    }

    private Console console(String platform, String name) {
        return consoleRepository.save(Console.builder().platform(platform).name(name).build());
    }

    private void consoleEntry(Game game, Console console) {
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(game).console(console).build());
    }
}
