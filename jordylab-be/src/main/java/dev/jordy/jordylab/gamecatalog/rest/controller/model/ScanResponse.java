package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;

import java.util.List;

/**
 * Result of a {@code POST /api/gamecatalog/ingest/scan} call. Mirrors the
 * shape of the legacy {@code SyncResponse} but drops the artworkRequested
 * field — artwork is now handled by the backend's external-lookup pipeline
 * and never uploaded by the script.
 */
public record ScanResponse(
        SyncOutcome outcome,
        boolean sourceEnabled,
        SyncCounts counts,
        List<EntryRejection> rejections) {
}
