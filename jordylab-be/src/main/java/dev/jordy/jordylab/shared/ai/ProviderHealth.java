package dev.jordy.jordylab.shared.ai;

import java.time.Instant;

public record ProviderHealth(
        String provider,
        boolean healthy,
        Instant lastCheckedAt,
        int ttlSeconds
) {
    public boolean isStale(int ttlSecondsConfig, java.time.Clock clock) {
        long elapsed = java.time.Duration.between(lastCheckedAt, Instant.now(clock)).getSeconds();
        return elapsed > ttlSecondsConfig;
    }
}
