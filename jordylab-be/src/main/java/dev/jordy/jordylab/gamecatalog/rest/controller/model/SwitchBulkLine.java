package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;
import java.util.UUID;

/**
 * One reviewed line of a Switch bulk add. Nothing is saved at preview time (spec 009 US3 AS1).
 *
 * @param line the pasted title, cleaned (trimmed, trademark signs removed)
 * @param candidates IGDB matches, best first; the admin may pick another one
 * @param existingGameId the catalog game when the status is {@link SwitchBulkStatus#ALREADY_PRESENT}
 * @param include whether the line is ticked for adding by default
 */
public record SwitchBulkLine(String line, SwitchBulkStatus status, List<SwitchSearchResult> candidates,
        UUID existingGameId, boolean include) {
}
