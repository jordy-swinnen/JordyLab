package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class GameInstallationTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f60");
    public static final String DEFAULT_EXTERNAL_REF = "Super Mario World (USA).smc";
    public static final String DEFAULT_PLATFORM = "SNES";
    public static final Instant DEFAULT_FIRST_SEEN = Instant.parse("2026-08-01T08:00:00Z");
    public static final Instant DEFAULT_LAST_SEEN = Instant.parse("2026-08-02T10:15:00Z");

    public static GameInstallation aDefaultGameInstallation() {
        return aGameInstallation().build();
    }

    public static GameInstallation.GameInstallationBuilder aGameInstallation() {
        return GameInstallation.builder()
                .id(DEFAULT_ID)
                .game(GameTestBuilder.aDefaultGame())
                .source(ScanSourceTestBuilder.aDefaultScanSource())
                .externalRef(DEFAULT_EXTERNAL_REF)
                .platform(DEFAULT_PLATFORM)
                .firstSeenAt(DEFAULT_FIRST_SEEN)
                .lastSeenAt(DEFAULT_LAST_SEEN);
    }
}
