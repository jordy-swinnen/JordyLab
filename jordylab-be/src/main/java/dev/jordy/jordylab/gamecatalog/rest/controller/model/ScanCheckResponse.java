package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/**
 * Response to {@code POST /api/gamecatalog/ingest/check}. {@code scanNeeded} is
 * false when the source is disabled (the client uploads nothing for a disabled
 * source).
 */
public record ScanCheckResponse(
        boolean scanNeeded,
        boolean sourceEnabled) {
}
