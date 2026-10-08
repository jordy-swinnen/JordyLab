package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class RefreshRunTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    public static final RefreshRunKind DEFAULT_KIND = RefreshRunKind.DATA;
    public static final int DEFAULT_TOTAL = 228;
    public static final String DEFAULT_STARTED_BY = "6e0f4d8a-3b21-4c57-9e8f-a1b2c3d4e5f6";
    public static final Instant DEFAULT_STARTED_AT = Instant.parse("2026-10-07T12:00:00Z");

    public static RefreshRun aDefaultRefreshRun() {
        return aRefreshRun().build();
    }

    public static RefreshRun.RefreshRunBuilder aRefreshRun() {
        return RefreshRun.builder()
                .id(DEFAULT_ID)
                .kind(DEFAULT_KIND)
                .total(DEFAULT_TOTAL)
                .startedBy(DEFAULT_STARTED_BY)
                .startedAt(DEFAULT_STARTED_AT);
    }
}
