package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.time.Instant;

/** The last run of a Steam library sync. Tokens and call counts are left out. */
public record LibrarySyncStatus(Instant lastSuccessAt, String lastOutcome, long entriesActive, boolean stale) {
}
