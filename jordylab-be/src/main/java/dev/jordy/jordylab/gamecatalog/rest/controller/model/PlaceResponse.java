package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;

import java.util.UUID;

/**
 * One place a game lives (spec 013 contracts/catalog-api.md): a scanned host copy, Steam library membership, or a
 * console. {@code label} is the host's display name when set, else its hostname, or the console's name; the raw hostname
 * is never returned here (FR-031). Fields that do not apply to a kind are null.
 */
public record PlaceResponse(
        PlaceKind kind,
        UUID installationId,
        UUID hostId,
        UUID consoleId,
        String label,
        String platform,
        Boolean installed,
        RomStatus romStatus,
        LibrarySource librarySource,
        String familyOwners) {

    /** The place a scanned copy is: its host's name, its platform and, for an emulated copy, whether the ROM launches. */
    public static PlaceResponse hostCopy(GameInstallation installation) {
        return new PlaceResponse(PlaceKind.HOST_COPY, installation.getId(), installation.getSource().getHost().getId(),
                null, installation.getSource().hostLabel(), installation.getPlatform(), true,
                installation.getRomStatus(), null, null);
    }

    public enum PlaceKind {
        HOST_COPY,
        STEAM_LIBRARY,
        CONSOLE
    }
}
