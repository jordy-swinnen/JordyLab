package dev.jordy.jordylab.fna;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a briefing finishes generating (spec 007 FR-016, research D9). Declared
 * in the module root package (the public API) so other modules, such as {@code mobile}'s
 * {@code @ApplicationModuleListener}, can depend on the event type without reaching into
 * {@code fna}'s internals.
 */
public record BriefingReady(UUID briefingId, Instant generatedAt) {
}
