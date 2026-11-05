package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One scan source. The name is the platform and the hostname is left out, since it identifies a machine; so are the
 * source key and the filesystem paths (FR-014).
 */
public record SourceSummary(UUID id, String name, String sourceType, boolean enabled, String lastOutcome,
                            Instant lastSuccessAt, long installedGames) {
}
