package dev.jordy.jordylab.shared.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches per-provider health status so that individual AI calls do not each incur a
 * health probe (FR-002) and the cached path adds no meaningful latency (NFR-001).
 */
@Component
@RequiredArgsConstructor
public class ProviderHealthCache {

    private final ConcurrentHashMap<String, ProviderHealth> cache = new ConcurrentHashMap<>();
    private final AiModuleConfig aiModuleConfig;
    private final Clock clock;

    public boolean isHealthy(String providerName) {
        ProviderHealth health = cache.get(providerName);

        if (health == null) {
            return true;
        }

        if (health.isStale(aiModuleConfig.healthCheckTtlSeconds(), clock)) {
            cache.remove(providerName);

            return true;
        }

        return health.healthy();
    }

    public void recordFailure(String providerName) {
        cache.put(providerName, new ProviderHealth(providerName, false, Instant.now(clock)));
    }

    public void recordSuccess(String providerName) {
        cache.put(providerName, new ProviderHealth(providerName, true, Instant.now(clock)));
    }
}
