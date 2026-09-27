package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;

import java.time.Instant;

public record LibrarySyncRunResponse(
        LibrarySource librarySource,
        LibrarySyncOutcome outcome,
        Instant startedAt,
        Instant finishedAt,
        int entriesSubmitted,
        int entriesAdded,
        int entriesRemoved,
        int metadataCalls,
        int aiCalls,
        String errorCode) {
}
