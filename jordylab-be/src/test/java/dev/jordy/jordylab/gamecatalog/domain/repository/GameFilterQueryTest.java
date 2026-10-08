package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.GameMark;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** Every filter of the library grid, alone and combined, against the real schema (spec 013 US7, US10, US13). */
@DataJpaTest
@Testcontainers
@Import(JpaConfiguration.class)
class GameFilterQueryTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final PageRequest PAGE = PageRequest.of(0, 50);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    @Autowired
    private GameInstallationRepository installationRepository;

    @Autowired
    private GameLibraryEntryRepository libraryEntryRepository;

    @Autowired
    private ConsoleRepository consoleRepository;

    @Autowired
    private ConsoleGameEntryRepository consoleGameEntryRepository;

    @Autowired
    private GameMarkRepository markRepository;

    private Game game(String title) {
        return gameRepository.save(Game.builder().title(title).build());
    }

    private Game gameWithPlayers(String title, Integer maxPlayers, Boolean local) {
        Game game = game(title);
        game.applyDeterministicMultiplayer(local, null, maxPlayers, MultiplayerSource.IGDB);

        return gameRepository.save(game);
    }

    private Host host(String hostname) {
        return hostRepository.save(Host.builder().hostname(hostname).build());
    }

    private ScanSource source(Host host, SourceType type) {
        return scanSourceRepository.save(ScanSource.builder().host(host).sourceType(type).enabled(true).build());
    }

    private GameInstallation install(Game game, ScanSource source, String platform) {
        return installationRepository.save(GameInstallation.builder().game(game).source(source)
                .externalRef(game.getTitle() + platform).platform(platform).firstSeenAt(NOW).lastSeenAt(NOW).build());
    }

    private void owned(Game game) {
        libraryEntryRepository.save(GameLibraryEntry.builder().game(game).librarySource(LibrarySource.OWNED)
                .firstSeenAt(NOW).lastSeenAt(NOW).build());
    }

    private void family(Game game) {
        libraryEntryRepository.save(GameLibraryEntry.builder().game(game).librarySource(LibrarySource.FAMILY)
                .firstSeenAt(NOW).lastSeenAt(NOW).build());
    }

    private Console console(String platform, String name) {
        return consoleRepository.save(Console.builder().platform(platform).name(name).build());
    }

    private void onConsole(Game game, Console console) {
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(game).console(console).build());
    }

    private void mark(Game game, String user, MarkType type) {
        markRepository.save(GameMark.builder().game(game).userSubject(user).mark(type).build());
    }

    private List<String> titles(GameFilter filter) {
        return gameRepository.findFiltered(filter, PAGE).getContent().stream().map(Game::getTitle).toList();
    }

    private GameFilter.GameFilterBuilder all() {
        return GameFilter.builder().installStatus("ALL");
    }

    @Test
    void withoutFiltersEveryVisibleGameComesBackInTitleOrderAndInvisibleOnesDoNot() {
        Host host = host("htpc");
        install(game("Zelda"), source(host, SourceType.EMUDECK), "SNES");
        owned(game("alpha"));
        game("Invisible");

        assertThat(titles(all().build())).containsExactly("alpha", "Zelda");
    }

    @Test
    void searchMatchesATitleSubstringIgnoringCase() {
        Host host = host("htpc");
        ScanSource emulator = source(host, SourceType.EMUDECK);
        install(game("Super Mario World"), emulator, "SNES");
        install(game("Metroid"), emulator, "SNES");

        assertThat(titles(all().search("MARIO").build())).containsExactly("Super Mario World");
    }

    @Test
    void installStatusSeparatesInstalledFromLibraryOnlyGames() {
        install(game("Installed"), source(host("htpc"), SourceType.STEAM), "Steam");
        owned(game("Library only"));

        assertSoftly(softly -> {
            softly.assertThat(titles(all().installStatus("INSTALLED").build())).containsExactly("Installed");
            softly.assertThat(titles(all().installStatus("NOT_INSTALLED").build())).containsExactly("Library only");
            softly.assertThat(titles(all().installStatus("ALL").build())).containsExactlyInAnyOrder("Installed", "Library only");
        });
    }

    @Test
    void severalPlatformsMatchAnyOfThemThroughEveryKindOfPlace() {
        ScanSource emulator = source(host("htpc"), SourceType.EMUDECK);
        install(game("On SNES"), emulator, "SNES");
        install(game("On N64"), emulator, "Nintendo 64");
        owned(game("On Steam"));
        onConsole(game("On Switch"), console("Nintendo Switch", "Dock"));

        assertSoftly(softly -> {
            softly.assertThat(titles(all().platforms(List.of("SNES", "Steam")).build()))
                    .containsExactlyInAnyOrder("On SNES", "On Steam");
            softly.assertThat(titles(all().platforms(List.of("Nintendo Switch")).build())).containsExactly("On Switch");
        });
    }

    @Test
    void whereMatchesHostsAndConsolesByIdAndAnyOfThem() {
        Host living = host("living");
        Host office = host("office");
        install(game("In living"), source(living, SourceType.STEAM), "Steam");
        install(game("In office"), source(office, SourceType.STEAM), "Steam");
        Console dock = console("Nintendo Switch", "Dock");
        onConsole(game("On dock"), dock);

        assertSoftly(softly -> {
            softly.assertThat(titles(all().whereIds(List.of(living.getId())).build())).containsExactly("In living");
            softly.assertThat(titles(all().whereIds(List.of(living.getId(), dock.getId())).build()))
                    .containsExactlyInAnyOrder("In living", "On dock");
        });
    }

    @Test
    void sourcesFollowTheFourLabelsAndAnyOfThem() {
        install(game("Steam installed only"), source(host("htpc"), SourceType.STEAM), "Steam");
        owned(game("Owned"));
        family(game("Family only"));
        Game both = game("Owned and family");
        owned(both);
        family(both);
        install(game("Emulated"), source(host("emu"), SourceType.EMUDECK), "SNES");
        onConsole(game("Console"), console("Nintendo Switch", "Dock"));

        assertSoftly(softly -> {
            softly.assertThat(titles(all().sources(List.of(GameSource.STEAM_OWNED)).build()))
                    .containsExactlyInAnyOrder("Steam installed only", "Owned", "Owned and family");
            softly.assertThat(titles(all().sources(List.of(GameSource.STEAM_FAMILY)).build())).containsExactly("Family only");
            softly.assertThat(titles(all().sources(List.of(GameSource.EMULATED, GameSource.CONSOLE)).build()))
                    .containsExactlyInAnyOrder("Emulated", "Console");
        });
    }

    @Test
    void minLocalPlayersReturnsConfirmedGamesAndCountsTheUnknownOnes() {
        ScanSource emulator = source(host("htpc"), SourceType.EMUDECK);
        install(gameWithPlayers("Eight", 8, true), emulator, "SNES");
        install(gameWithPlayers("Two", 2, true), emulator, "SNES");
        install(gameWithPlayers("Local but how many", null, true), emulator, "SNES");
        install(gameWithPlayers("Unknown", null, null), emulator, "SNES");
        install(gameWithPlayers("Not local", null, false), emulator, "SNES");
        GameFilter filter = all().minLocalPlayers(4).build();

        assertSoftly(softly -> {
            softly.assertThat(titles(filter)).containsExactly("Eight");
            softly.assertThat(gameRepository.countWithUnknownPlayerCount(filter)).isEqualTo(2);
        });
    }

    @Test
    void romStatusMatchesGamesWithAtLeastOneEmulatedCopyInThatStatus() {
        ScanSource emulator = source(host("htpc"), SourceType.EMUDECK);
        ScanSource second = source(host("laptop"), SourceType.EMUDECK);
        Game mixed = game("Mixed");
        install(mixed, emulator, "SNES").changeRomStatus(RomStatus.BROKEN);
        install(mixed, second, "SNES").changeRomStatus(RomStatus.VALIDATED);
        install(game("Untouched"), emulator, "SNES");
        installationRepository.flush();

        assertSoftly(softly -> {
            softly.assertThat(titles(all().romStatuses(List.of(RomStatus.BROKEN)).build())).containsExactly("Mixed");
            softly.assertThat(titles(all().romStatuses(List.of(RomStatus.VALIDATED)).build())).containsExactly("Mixed");
            softly.assertThat(titles(all().romStatuses(List.of(RomStatus.UNKNOWN)).build())).containsExactly("Untouched");
        });
    }

    @Test
    void markFiltersLookAtEveryonesMarksOrOnlyTheAskers() {
        ScanSource emulator = source(host("htpc"), SourceType.EMUDECK);
        Game wantedByOthers = game("Wanted by others");
        Game wantedByMe = game("Wanted by me");
        Game liked = game("Liked");
        for (Game each : List.of(wantedByOthers, wantedByMe, liked)) {
            install(each, emulator, "SNES");
        }
        mark(wantedByOthers, "bob", MarkType.WANT_TO_PLAY);
        mark(wantedByMe, "alice", MarkType.WANT_TO_PLAY);
        mark(liked, "bob", MarkType.PLAYED_LIKED);

        assertSoftly(softly -> {
            softly.assertThat(titles(all().marks(List.of(MarkType.WANT_TO_PLAY)).build()))
                    .containsExactlyInAnyOrder("Wanted by others", "Wanted by me");
            softly.assertThat(titles(all().marks(List.of(MarkType.WANT_TO_PLAY))
                    .markScope(GameFilter.MarkScope.MINE).userSubject("alice").build())).containsExactly("Wanted by me");
            softly.assertThat(titles(all().marks(List.of(MarkType.WANT_TO_PLAY, MarkType.PLAYED_LIKED)).build()))
                    .containsExactlyInAnyOrder("Wanted by others", "Wanted by me", "Liked");
        });
    }

    @Test
    void mostWantedAndMostLikedOrderByTheNumberOfThoseVotes() {
        ScanSource emulator = source(host("htpc"), SourceType.EMUDECK);
        Game popular = game("Popular");
        Game quiet = game("Another");
        Game loved = game("Loved");
        for (Game each : List.of(popular, quiet, loved)) {
            install(each, emulator, "SNES");
        }
        mark(popular, "a", MarkType.WANT_TO_PLAY);
        mark(popular, "b", MarkType.WANT_TO_PLAY);
        mark(quiet, "a", MarkType.WANT_TO_PLAY);
        mark(loved, "a", MarkType.PLAYED_LIKED);
        mark(loved, "b", MarkType.PLAYED_LIKED);
        mark(loved, "c", MarkType.PLAYED_LIKED);

        assertSoftly(softly -> {
            softly.assertThat(titles(all().sort(GameFilter.Sort.MOST_WANTED).build()))
                    .containsExactly("Popular", "Another", "Loved");
            softly.assertThat(titles(all().sort(GameFilter.Sort.MOST_LIKED).build()))
                    .containsExactly("Loved", "Another", "Popular");
        });
    }

    @Test
    void combinedFiltersMustAllHoldAndPagingReportsTheTotal() {
        ScanSource emulator = source(host("htpc"), SourceType.EMUDECK);
        for (int index = 0; index < 5; index++) {
            install(gameWithPlayers("Party " + index, 8, true), emulator, "SNES");
        }
        install(gameWithPlayers("Party solo", 1, true), emulator, "SNES");
        GameFilter filter = all().search("party").platforms(List.of("SNES")).minLocalPlayers(4).build();

        var firstPage = gameRepository.findFiltered(filter, PageRequest.of(0, 2));

        assertSoftly(softly -> {
            softly.assertThat(firstPage.getContent()).extracting(Game::getTitle).containsExactly("Party 0", "Party 1");
            softly.assertThat(firstPage.getTotalElements()).isEqualTo(5);
            softly.assertThat(firstPage.getTotalPages()).isEqualTo(3);
        });
    }

    @Test
    void aSearchTextWithQuerySyntaxIsJustText() {
        install(game("Plain"), source(host("htpc"), SourceType.EMUDECK), "SNES");

        assertThat(titles(all().search("' OR 1=1 --").build())).isEmpty();
    }
}
