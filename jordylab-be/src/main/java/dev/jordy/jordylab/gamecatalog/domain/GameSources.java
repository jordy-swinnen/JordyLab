package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Derives the source labels of one game from its places (spec 013 FR-039, data-model "Derived source labels"):
 * Steam (Owned) = an active OWNED library entry, or an installed Steam copy with no active library entry at all (until a
 * sync resolves it); Steam (Family) = an active FAMILY entry and no active OWNED entry; Emulated = an installed copy on an
 * enabled emulation source; Console = at least one console entry.
 */
@UtilityClass
public class GameSources {

    public static Set<GameSource> of(Collection<GameInstallation> installations,
            Collection<GameLibraryEntry> libraryEntries, Collection<ConsoleGameEntry> consoleEntries) {
        Set<GameSource> sources = EnumSet.noneOf(GameSource.class);
        boolean activeOwned = libraryEntries.stream().anyMatch(entry -> entry.isActive()
                && entry.getLibrarySource() == LibrarySource.OWNED);
        boolean activeFamily = libraryEntries.stream().anyMatch(entry -> entry.isActive()
                && entry.getLibrarySource() == LibrarySource.FAMILY);
        boolean anyActiveLibraryEntry = activeOwned || activeFamily;
        boolean installedSteamCopy = installations.stream()
                .anyMatch(installation -> isEffective(installation) && installation.getSource().getSourceType() == SourceType.STEAM);
        if (activeOwned || (installedSteamCopy && !anyActiveLibraryEntry)) {
            sources.add(GameSource.STEAM_OWNED);
        }
        if (activeFamily && !activeOwned) {
            sources.add(GameSource.STEAM_FAMILY);
        }
        if (installations.stream().anyMatch(installation -> isEffective(installation) && installation.isEmulated())) {
            sources.add(GameSource.EMULATED);
        }
        if (!consoleEntries.isEmpty()) {
            sources.add(GameSource.CONSOLE);
        }

        return sources;
    }

    /** An installed copy on an enabled source: the only kind of scan copy that counts as a place right now. */
    public static boolean isEffective(GameInstallation installation) {
        return installation.isInstalled() && installation.getSource().isEnabled();
    }
}
