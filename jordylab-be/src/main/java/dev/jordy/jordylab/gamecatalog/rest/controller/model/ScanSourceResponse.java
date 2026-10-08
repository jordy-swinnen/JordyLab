package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;

import java.time.Instant;
import java.util.UUID;

public record ScanSourceResponse(
        UUID id,
        String sourceKey,
        UUID hostId,
        String hostname,
        String displayName,
        String label,
        SourceType sourceType,
        String platform,
        PlatformChip platformChip,
        boolean enabled,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        Instant lastCheckedAt,
        SyncOutcome lastOutcome,
        long installedGameCount) {
}
