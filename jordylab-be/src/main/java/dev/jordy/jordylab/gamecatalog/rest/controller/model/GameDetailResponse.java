package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameDetailResponse(
        UUID id,
        String title,
        List<PlatformChip> platforms,
        List<GameSource> sources,
        List<PlaceResponse> places,
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
        DescriptionResponse description,
        Instant firstSeenAt,
        InstallStatus installStatus,
        Boolean localMultiplayer,
        Boolean splitScreen,
        Boolean onlineOnly,
        MultiplayerSource multiplayerSource,
        FactSourcesResponse factSources,
        VoteTotalsResponse votes,
        MarkType myMark) {
}
