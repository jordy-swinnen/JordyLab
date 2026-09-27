package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameDetailResponse(
        UUID id,
        String title,
        String platform,
        List<HostRef> hosts,
        ArtworkStatus coverStatus,
        String coverUrl,
        String coverEndpoint,
        ArtworkStatus bannerStatus,
        String bannerUrl,
        String bannerEndpoint,
        EnrichmentStatus enrichmentStatus,
        String genre,
        String genres,
        String developer,
        String publisher,
        Integer releaseYear,
        String metadataSource,
        Integer maxLocalPlayers,
        Boolean onlineMultiplayer,
        Boolean singlePlayer,
        String description,
        Instant firstSeenAt,
        InstallStatus installStatus,
        LibrarySource librarySource,
        List<String> familyOwners,
        Boolean localMultiplayer,
        Boolean splitScreen,
        Boolean onlineOnly,
        MultiplayerSource multiplayerSource) {
}
