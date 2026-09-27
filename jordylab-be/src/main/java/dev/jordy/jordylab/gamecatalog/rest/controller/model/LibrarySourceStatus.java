package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;

import java.time.Instant;

public record LibrarySourceStatus(
        Instant lastSuccessAt,
        LibrarySyncOutcome lastOutcome,
        long entriesActive,
        int metadataCalls,
        int aiCalls,
        boolean familyTokenPresent,
        boolean stale) {
}
