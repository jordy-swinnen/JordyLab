package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;

import java.util.UUID;

public record GameSummaryResponse(
        UUID id,
        String title,
        String platform,
        ArtworkStatus artworkStatus,
        String artworkUrl,
        String artworkEndpoint) {
}
