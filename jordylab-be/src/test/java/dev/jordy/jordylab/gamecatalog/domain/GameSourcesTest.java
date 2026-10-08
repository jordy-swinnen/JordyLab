package dev.jordy.jordylab.gamecatalog.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GameSourcesTest {

    private static final Instant SEEN = Instant.parse("2026-10-07T10:00:00Z");

    @Test
    void anActiveOwnedLibraryEntryIsSteamOwned() {
        Set<GameSource> sources = GameSources.of(List.of(), List.of(libraryEntry(LibrarySource.OWNED, true)), List.of());

        assertThat(sources).containsExactly(GameSource.STEAM_OWNED);
    }

    @Test
    void aFamilyEntryWithoutAnOwnedEntryIsSteamFamily() {
        Set<GameSource> sources = GameSources.of(List.of(), List.of(libraryEntry(LibrarySource.FAMILY, true)), List.of());

        assertThat(sources).containsExactly(GameSource.STEAM_FAMILY);
    }

    @Test
    void ownedWinsOverFamilyWhenBothAreActive() {
        Set<GameSource> sources = GameSources.of(List.of(),
                List.of(libraryEntry(LibrarySource.OWNED, true), libraryEntry(LibrarySource.FAMILY, true)), List.of());

        assertThat(sources).containsExactly(GameSource.STEAM_OWNED);
    }

    @Test
    void aRemovedLibraryEntryDoesNotCount() {
        Set<GameSource> sources = GameSources.of(List.of(), List.of(libraryEntry(LibrarySource.OWNED, false)), List.of());

        assertThat(sources).isEmpty();
    }

    @Test
    void anInstalledSteamCopyWithNoLibraryEntryIsTreatedAsSteamOwnedUntilASyncDecides() {
        Set<GameSource> sources = GameSources.of(List.of(installation(SourceType.STEAM, true, true)), List.of(), List.of());

        assertThat(sources).containsExactly(GameSource.STEAM_OWNED);
    }

    @Test
    void anInstalledSteamCopyOfAFamilyGameDoesNotAlsoBecomeSteamOwned() {
        Set<GameSource> sources = GameSources.of(List.of(installation(SourceType.STEAM, true, true)),
                List.of(libraryEntry(LibrarySource.FAMILY, true)), List.of());

        assertThat(sources).containsExactly(GameSource.STEAM_FAMILY);
    }

    @Test
    void anInstalledEmulatedCopyIsEmulated() {
        Set<GameSource> sources = GameSources.of(List.of(installation(SourceType.EMUDECK, true, true)), List.of(), List.of());

        assertThat(sources).containsExactly(GameSource.EMULATED);
    }

    @Test
    void anEmulatedCopyOnADisabledSourceOrNotInstalledDoesNotCount() {
        Set<GameSource> sources = GameSources.of(List.of(installation(SourceType.EMUDECK, true, false),
                installation(SourceType.EMUDECK, false, true)), List.of(), List.of());

        assertThat(sources).isEmpty();
    }

    @Test
    void aConsoleEntryIsConsole() {
        Set<GameSource> sources = GameSources.of(List.of(), List.of(), List.of(ConsoleGameEntryTestBuilder.aDefaultConsoleGameEntry()));

        assertThat(sources).containsExactly(GameSource.CONSOLE);
    }

    @Test
    void aGameCanCarryEverySourceAtOnce() {
        Set<GameSource> sources = GameSources.of(List.of(installation(SourceType.EMUDECK, true, true)),
                List.of(libraryEntry(LibrarySource.OWNED, true)), List.of(ConsoleGameEntryTestBuilder.aDefaultConsoleGameEntry()));

        assertThat(sources).containsExactlyInAnyOrder(GameSource.STEAM_OWNED, GameSource.EMULATED, GameSource.CONSOLE);
    }

    @Test
    void labelsAreTheOnesTheOwnerChose() {
        assertThat(List.of(GameSource.values())).extracting(GameSource::label)
                .containsExactly("Steam (Owned)", "Steam (Family)", "Emulated", "Console");
    }

    private static GameLibraryEntry libraryEntry(LibrarySource source, boolean active) {
        return GameLibraryEntry.builder().game(GameTestBuilder.aDefaultGame()).librarySource(source)
                .firstSeenAt(SEEN).lastSeenAt(SEEN).removedAt(active ? null : SEEN).build();
    }

    private static GameInstallation installation(SourceType sourceType, boolean installed, boolean sourceEnabled) {
        ScanSource source = ScanSourceTestBuilder.aScanSource().sourceType(sourceType).enabled(sourceEnabled).build();
        GameInstallation installation = GameInstallationTestBuilder.aGameInstallation().source(source)
                .platform("SNES").build();
        if (!installed) {
            installation.markUninstalled(SEEN);
        }

        return installation;
    }
}
