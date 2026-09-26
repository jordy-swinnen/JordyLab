package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;

import java.time.Instant;
import java.util.UUID;

public record GameDetailResponse(
        UUID id,
        String title,
        String platform,
        String sourceKey,
        ArtworkStatus artworkStatus,
        String artworkUrl,
        String artworkEndpoint,
        EnrichmentStatus enrichmentStatus,
        String genre,
        Integer maxLocalPlayers,
        Boolean onlineMultiplayer,
        Boolean singlePlayer,
        String description,
        Instant firstSeenAt) {
}
