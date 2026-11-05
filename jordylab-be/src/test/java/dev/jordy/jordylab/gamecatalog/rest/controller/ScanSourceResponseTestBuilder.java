package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanSourceResponse;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class ScanSourceResponseTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("99999999-0000-0000-0000-000000000009");
    public static final String DEFAULT_PLATFORM = "Steam";
    public static final String DEFAULT_HOSTNAME = "jordy-desktop";
    public static final Instant DEFAULT_LAST_SUCCESS_AT = Instant.parse("2026-11-03T08:00:00Z");

    public static ScanSourceResponse aDefaultScanSourceResponse() {
        return new ScanSourceResponse(
                DEFAULT_ID,
                "machine-key-do-not-expose",
                null,
                DEFAULT_HOSTNAME,
                null,
                "Steam library on " + DEFAULT_HOSTNAME,
                SourceType.STEAM,
                DEFAULT_PLATFORM,
                null,
                true,
                DEFAULT_LAST_SUCCESS_AT,
                DEFAULT_LAST_SUCCESS_AT,
                DEFAULT_LAST_SUCCESS_AT,
                SyncOutcome.APPLIED,
                42L);
    }
}
