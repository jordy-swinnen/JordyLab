package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

/** One IGDB match for a search on a console's platform. */
public record ConsoleSearchResult(long igdbGameId, String title, Integer releaseYear, List<String> genres,
        String developer, String coverUrl, String bannerUrl, boolean alreadyOnConsole) {
}
