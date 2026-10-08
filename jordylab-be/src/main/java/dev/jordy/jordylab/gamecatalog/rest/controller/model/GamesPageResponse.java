package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

/**
 * One page of the library. {@code unknownPlayerCount} is present only when a minimum number of local players was asked
 * for: how many more games might fit but have no player count yet (they are left out, never presented as matches).
 */
public record GamesPageResponse(
        List<GameSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Long unknownPlayerCount) {
}
