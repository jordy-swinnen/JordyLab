package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;

import java.time.Instant;
import java.util.UUID;

public record ScanSourceResponse(
        UUID id,
        String sourceKey,
        String hostname,
        SourceType sourceType,
        String platform,
        boolean enabled,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        SyncOutcome lastOutcome,
        long installedGameCount) {
}
