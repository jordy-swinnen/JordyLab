package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

public record GamesPageResponse(
        List<GameSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
