package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;

import java.util.List;
import java.util.UUID;

public record GameSummaryResponse(
        UUID id,
        String title,
        List<PlatformChip> platforms,
        List<GameSource> sources,
        ArtworkStatus coverStatus,
        String coverUrl,
        String coverEndpoint,
        InstallStatus installStatus,
        Boolean localMultiplayer,
        VoteTotalsResponse votes,
        MarkType myMark,
        RomSummaryResponse romSummary) {
}
