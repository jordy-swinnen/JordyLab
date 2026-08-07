package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class ScanSourceTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("7a3b8c2e-1f4d-4a5b-9c6d-2e8f0a1b3c4d");
    public static final String DEFAULT_HOSTNAME = "jordybox";
    public static final SourceType DEFAULT_SOURCE_TYPE = SourceType.STEAM;
    public static final Instant DEFAULT_SYNC_TIME = Instant.parse("2026-08-02T10:15:00Z");

    public static ScanSource aDefaultScanSource() {
        return aScanSource().build();
    }

    public static ScanSource.ScanSourceBuilder aScanSource() {
        return ScanSource.builder()
                .id(DEFAULT_ID)
                .hostname(DEFAULT_HOSTNAME)
                .sourceType(DEFAULT_SOURCE_TYPE)
                .enabled(true);
    }
}
