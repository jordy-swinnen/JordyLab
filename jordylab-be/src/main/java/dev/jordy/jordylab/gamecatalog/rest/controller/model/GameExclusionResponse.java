package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One game the admin hid from the catalog, for the Sources exclusions list (JOR-97). */
public record GameExclusionResponse(UUID id, String title, List<String> platforms, Instant excludedAt) {
}
