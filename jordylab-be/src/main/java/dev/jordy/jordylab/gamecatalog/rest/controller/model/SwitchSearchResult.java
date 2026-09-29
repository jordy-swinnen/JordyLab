package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

/**
 * API response for one IGDB Switch search result.
 */
public record SwitchSearchResult(long igdbGameId, String title, Integer releaseYear, List<String> genres,
        String developer, String coverUrl, String bannerUrl) {
}
