package dev.jordy.jordylab.shared.ai;

import dev.jordy.jordylab.shared.util.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderHealthCacheTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String PROVIDER = "anthropic";
    private static final int TTL_SECONDS = 30;

    @Test
    void healthyWhenNoEntryExists() {
        ProviderHealthCache cache = new ProviderHealthCache(AiModuleConfigTestBuilder.aDefaultAiModuleConfig(), fixedClock());

        boolean result = cache.isHealthy(PROVIDER);

        assertThat(result).isTrue();
    }

    @Test
    void healthyAfterSuccess() {
        ProviderHealthCache cache = new ProviderHealthCache(AiModuleConfigTestBuilder.aDefaultAiModuleConfig(), fixedClock());

        cache.recordSuccess(PROVIDER);
        boolean result = cache.isHealthy(PROVIDER);

        assertThat(result).isTrue();
    }

    @Test
    void unhealthyAfterFailure() {
        ProviderHealthCache cache = new ProviderHealthCache(AiModuleConfigTestBuilder.aDefaultAiModuleConfig(), fixedClock());

        cache.recordFailure(PROVIDER);
        boolean result = cache.isHealthy(PROVIDER);

        assertThat(result).isFalse();
    }

    @Test
    void reProbesAfterTtlExpiry() {
        MutableClock clock = fixedClock();
        ProviderHealthCache cache = new ProviderHealthCache(AiModuleConfigTestBuilder.anAiModuleConfigWithTtl(TTL_SECONDS), clock);

        cache.recordSuccess(PROVIDER);
        clock.setInstant(NOW.plusSeconds(TTL_SECONDS + 5));
        boolean result = cache.isHealthy(PROVIDER);

        assertThat(result).isTrue();
    }

    @Test
    void failureInvalidatesCachedHealth() {
        ProviderHealthCache cache = new ProviderHealthCache(AiModuleConfigTestBuilder.aDefaultAiModuleConfig(), fixedClock());

        cache.recordSuccess(PROVIDER);
        cache.recordFailure(PROVIDER);

        assertThat(cache.isHealthy(PROVIDER)).isFalse();
    }

    private MutableClock fixedClock() {
        return new MutableClock(NOW, ZoneOffset.UTC);
    }
}
