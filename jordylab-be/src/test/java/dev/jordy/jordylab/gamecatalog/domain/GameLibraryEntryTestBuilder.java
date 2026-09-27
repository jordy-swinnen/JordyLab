package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class GameLibraryEntryTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f");
    public static final Instant DEFAULT_FIRST_SEEN = Instant.parse("2026-01-13T00:00:00Z");
    public static final Instant DEFAULT_LAST_SEEN = Instant.parse("2026-01-14T00:00:00Z");

    public static GameLibraryEntry aDefaultGameLibraryEntry() {
        return aGameLibraryEntry().build();
    }

    public static GameLibraryEntry.GameLibraryEntryBuilder aGameLibraryEntry() {
        return GameLibraryEntry.builder()
                .id(DEFAULT_ID)
                .game(GameTestBuilder.aDefaultGame())
                .librarySource(LibrarySource.OWNED)
                .firstSeenAt(DEFAULT_FIRST_SEEN)
                .lastSeenAt(DEFAULT_LAST_SEEN);
    }
}
