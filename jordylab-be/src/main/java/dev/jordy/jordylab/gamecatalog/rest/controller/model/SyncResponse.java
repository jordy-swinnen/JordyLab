package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;

import java.util.List;

public record SyncResponse(
        SyncOutcome outcome,
        boolean sourceEnabled,
        SyncCounts counts,
        List<EntryRejection> rejections,
        List<String> artworkRequested) {
}
