package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;

import java.util.List;
import java.util.UUID;

/**
 * One game in a search result. {@code localMultiplayer} is null while the fact is unknown (FR-010). Hidden fields such as
 * artwork links and voter identities are not part of this record.
 */
public record GameSearchItem(UUID id, String title, List<String> platforms, List<String> sources, boolean installed,
                             Boolean localMultiplayer, MarkTotals markTotals, MarkType myMark) {
}
