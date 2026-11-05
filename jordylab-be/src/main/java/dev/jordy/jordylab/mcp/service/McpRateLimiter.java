package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.mcp.McpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fixed one-minute window per admin (FR-020, data-model.md "In-memory state"). The counter lives in process memory, so a
 * restart resets it; there is one instance and that is accepted (spec assumption). A refused call does not count.
 */
@Component
@RequiredArgsConstructor
public class McpRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final McpProperties mcpProperties;
    private final Clock clock;
    private final Map<String, Window> windowsBySubject = new HashMap<>();

    public synchronized boolean tryAcquire(String subject) {
        Instant now = clock.instant();
        Window window = windowsBySubject.get(subject);
        if (window == null || !now.isBefore(window.start().plus(WINDOW))) {
            window = new Window(now, 0);
        }
        if (window.count() >= mcpProperties.toolCallsPerMinute()) {
            windowsBySubject.put(subject, window);
            return false;
        }
        windowsBySubject.put(subject, new Window(window.start(), window.count() + 1));
        return true;
    }

    private record Window(Instant start, int count) {
    }
}
