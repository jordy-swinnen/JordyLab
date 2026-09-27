package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class LibrarySyncRunTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("3d4e5f6a-7b8c-4d9e-8f01-2a3b4c5d6e7f");
    public static final Instant DEFAULT_STARTED_AT = Instant.parse("2026-01-13T00:00:00Z");
    public static final Instant DEFAULT_FINISHED_AT = Instant.parse("2026-01-13T00:00:04Z");

    public static LibrarySyncRun aDefaultLibrarySyncRun() {
        return aLibrarySyncRun().build();
    }

    public static LibrarySyncRun.LibrarySyncRunBuilder aLibrarySyncRun() {
        return LibrarySyncRun.builder()
                .id(DEFAULT_ID)
                .librarySource(LibrarySource.OWNED)
                .startedAt(DEFAULT_STARTED_AT)
                .finishedAt(DEFAULT_FINISHED_AT)
                .outcome(LibrarySyncOutcome.APPLIED);
    }
}
