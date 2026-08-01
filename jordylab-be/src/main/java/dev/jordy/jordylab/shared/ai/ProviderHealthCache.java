package dev.jordy.jordylab.shared.ai;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProviderHealthCache {

    private final ConcurrentHashMap<String, ProviderHealth> cache = new ConcurrentHashMap<>();
    private final AiModuleConfig aiModuleConfig;
    private final Clock clock;

    @Autowired
    public ProviderHealthCache(AiModuleConfig aiModuleConfig) {
        this(aiModuleConfig, Clock.systemUTC());
    }

    ProviderHealthCache(AiModuleConfig aiModuleConfig, Clock clock) {
        this.aiModuleConfig = aiModuleConfig;
        this.clock = clock;
    }

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
        cache.remove(providerName);

        cache.put(providerName, new ProviderHealth(providerName, false, Instant.now(clock), aiModuleConfig.healthCheckTtlSeconds()));
    }

    public void recordSuccess(String providerName) {
        cache.put(providerName, new ProviderHealth(providerName, true, Instant.now(clock), aiModuleConfig.healthCheckTtlSeconds()));
    }
}
