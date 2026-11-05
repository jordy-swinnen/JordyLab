package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;

import java.util.List;
import java.util.UUID;

/**
 * One game in full. Facts the catalog has not confirmed are null, never inferred (FR-010). The description carries its
 * source ("STEAM" or "AI").
 */
public record GameDetail(UUID id, String title, List<String> platforms, List<String> sources, boolean installed,
                         List<PlaceRef> places, String genres, String developer, String publisher, Integer releaseYear,
                         String description, String descriptionSource, Integer maxLocalPlayers, Boolean localMultiplayer,
                         Boolean splitScreen, Boolean onlineOnly, Boolean onlineMultiplayer, Boolean singlePlayer,
                         MarkTotals markTotals, MarkType myMark) {
}
