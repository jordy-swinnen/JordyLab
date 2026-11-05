package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibrarySourceStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryStatusResponse;
import lombok.experimental.UtilityClass;

import java.time.Instant;

@UtilityClass
class LibraryStatusResponseTestBuilder {

    public static final Instant DEFAULT_LAST_SUCCESS_AT = Instant.parse("2026-11-03T07:00:00Z");

    public static LibraryStatusResponse aDefaultLibraryStatusResponse() {
        return new LibraryStatusResponse(
                new LibrarySourceStatus(DEFAULT_LAST_SUCCESS_AT, LibrarySyncOutcome.APPLIED, 42L, 3, 1, true, false),
                new LibrarySourceStatus(null, null, 0L, 0, 0, false, true),
                true);
    }
}
