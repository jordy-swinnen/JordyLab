package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;

import java.util.UUID;

public record GameSummaryResponse(
        UUID id,
        String title,
        String platform,
        ArtworkStatus coverStatus,
        String coverUrl,
        String coverEndpoint,
        InstallStatus installStatus,
        LibrarySource librarySource,
        Boolean localMultiplayer) {
}
