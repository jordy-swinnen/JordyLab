package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.DescriptionSource;

import java.time.Instant;

/**
 * A game's description and where it came from (spec 013 FR-059). For {@code AI}, {@code model} is what the provider reported as
 * answering (null for text written before this was recorded or when the provider reported none) and {@code requestedModel}
 * the selected id, present only when it differs (a router, or the fallback provider). The page builds its heading from these
 * fields alone.
 */
public record DescriptionResponse(String text, DescriptionSource source, String model, String requestedModel,
        Instant writtenAt) {
}
