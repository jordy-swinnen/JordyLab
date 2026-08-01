package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderHealthCacheTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void healthyWhenNoEntryExists() {
        ProviderHealthCache cache = new ProviderHealthCache(configWithTtl(30), fixedClock(NOW));

        boolean result = cache.isHealthy("anthropic");

        assertThat(result).isTrue();
    }

    @Test
    void healthyAfterSuccess() {
        ProviderHealthCache cache = new ProviderHealthCache(configWithTtl(30), fixedClock(NOW));

        cache.recordSuccess("anthropic");
        boolean result = cache.isHealthy("anthropic");

        assertThat(result).isTrue();
    }

    @Test
    void unhealthyAfterFailure() {
        ProviderHealthCache cache = new ProviderHealthCache(configWithTtl(30), fixedClock(NOW));

        cache.recordFailure("anthropic");
        boolean result = cache.isHealthy("anthropic");

        assertThat(result).isFalse();
    }

    @Test
    void reProbesAfterTtlExpiry() {
        MutableClock clock = fixedClock(NOW);
        ProviderHealthCache cache = new ProviderHealthCache(configWithTtl(30), clock);

        cache.recordSuccess("anthropic");
        clock.setInstant(NOW.plusSeconds(35));
        boolean result = cache.isHealthy("anthropic");

        assertThat(result).isTrue();
    }

    @Test
    void failureInvalidatesCachedHealth() {
        ProviderHealthCache cache = new ProviderHealthCache(configWithTtl(30), fixedClock(NOW));

        cache.recordSuccess("anthropic");
        cache.recordFailure("anthropic");

        assertThat(cache.isHealthy("anthropic")).isFalse();
    }

    private AiModuleConfig configWithTtl(int ttlSeconds) {
        return new AiModuleConfig(ttlSeconds, 2, java.util.Map.of());
    }

    private MutableClock fixedClock(Instant instant) {
        return new MutableClock(instant, ZoneOffset.UTC);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneOffset zone;

        MutableClock(Instant instant, ZoneOffset zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return zone;
        }

        @Override
        public Clock withZone(java.time.ZoneId zoneId) {
            return new MutableClock(instant, ZoneOffset.of(zoneId.getId()));
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
