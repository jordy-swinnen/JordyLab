package dev.jordy.jordylab.fna.rest.controller.model;

import java.time.Instant;

/**
 * The latest briefing, or {@code reason = NO_BRIEFING_YET} with no content when none exists (User Story 2, scenario 4).
 * Reading it never triggers a new briefing.
 */
public record BriefingView(Instant generatedAt, String modelUsed, String content, String reason) {
}
