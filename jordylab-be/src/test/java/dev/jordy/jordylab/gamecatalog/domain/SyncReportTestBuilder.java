package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class SyncReportTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("9f8e7d6c-5b4a-43c2-b1a0-9f8e7d6c5b4a");
    public static final Instant DEFAULT_RECEIVED_AT = Instant.parse("2026-08-02T10:20:00Z");
    public static final String DEFAULT_PAYLOAD_HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    public static SyncReport aDefaultSyncReport() {
        return aSyncReport().build();
    }

    public static SyncReport.SyncReportBuilder aSyncReport() {
        return SyncReport.builder()
                .id(DEFAULT_ID)
                .source(ScanSourceTestBuilder.aDefaultScanSource())
                .receivedAt(DEFAULT_RECEIVED_AT)
                .outcome(SyncOutcome.APPLIED)
                .payloadHash(DEFAULT_PAYLOAD_HASH);
    }
}
