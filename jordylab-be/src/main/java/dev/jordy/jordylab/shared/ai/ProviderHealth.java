package dev.jordy.jordylab.shared.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public record ProviderHealth(
        String provider,
        boolean healthy,
        Instant lastCheckedAt
) {
    public boolean isStale(int ttlSeconds, Clock clock) {
        long elapsed = Duration.between(lastCheckedAt, Instant.now(clock)).getSeconds();

        return elapsed > ttlSeconds;
    }
}
