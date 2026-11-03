package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.mcp.McpProperties;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class McpRateLimiterTest {

    private static final String FIRST_ADMIN = "00000000-0000-0000-0000-00000000000a";
    private static final String SECOND_ADMIN = "00000000-0000-0000-0000-00000000000b";
    private static final Instant START = Instant.parse("2026-11-03T10:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new SteppingClock(now);
    private final McpRateLimiter limiter = new McpRateLimiter(new McpProperties("", "jordylab-mcp", 60), clock);

    @Test
    void acceptsSixtyCallsAndRefusesTheSixtyFirstInTheSameMinute() {
        for (int call = 0; call < 60; call++) {
            assertSoftly(softly -> softly.assertThat(limiter.tryAcquire(FIRST_ADMIN)).isTrue());
        }

        assertSoftly(softly -> softly.assertThat(limiter.tryAcquire(FIRST_ADMIN)).isFalse());
    }

    @Test
    void reopensAfterOneMinute() {
        for (int call = 0; call < 60; call++) {
            limiter.tryAcquire(FIRST_ADMIN);
        }
        now.set(START.plus(Duration.ofMinutes(1)));

        assertSoftly(softly -> softly.assertThat(limiter.tryAcquire(FIRST_ADMIN)).isTrue());
    }

    @Test
    void countsEachAdminSeparately() {
        for (int call = 0; call < 60; call++) {
            limiter.tryAcquire(FIRST_ADMIN);
        }

        assertSoftly(softly -> softly.assertThat(limiter.tryAcquire(SECOND_ADMIN)).isTrue());
    }

    /** A clock whose instant the test moves forward through an atomic reference. */
    @RequiredArgsConstructor
    private static final class SteppingClock extends Clock {

        private final AtomicReference<Instant> now;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
