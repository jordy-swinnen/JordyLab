package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class GameTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    public static final String DEFAULT_EXTERNAL_REF = "Super Mario World (USA).smc";
    public static final String DEFAULT_TITLE = "Super Mario World";
    public static final String DEFAULT_PLATFORM = "SNES";
    public static final Instant DEFAULT_FIRST_SEEN = Instant.parse("2026-08-01T08:00:00Z");
    public static final Instant DEFAULT_LAST_SEEN = Instant.parse("2026-08-02T10:15:00Z");

    public static Game aDefaultGame() {
        return aGame().build();
    }

    public static Game.GameBuilder aGame() {
        return Game.builder()
                .id(DEFAULT_ID)
                .source(ScanSourceTestBuilder.aDefaultScanSource())
                .platform(DEFAULT_PLATFORM)
                .externalRef(DEFAULT_EXTERNAL_REF)
                .title(DEFAULT_TITLE)
                .firstSeenAt(DEFAULT_FIRST_SEEN)
                .lastSeenAt(DEFAULT_LAST_SEEN);
    }
}
