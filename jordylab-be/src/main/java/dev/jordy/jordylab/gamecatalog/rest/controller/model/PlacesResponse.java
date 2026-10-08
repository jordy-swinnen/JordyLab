package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;
import java.util.UUID;

/** The hosts and consoles that hold visible games, for the "Where" filter. */
public record PlacesResponse(List<PlaceOption> places) {

    public enum Kind {
        HOST,
        CONSOLE
    }

    public record PlaceOption(UUID id, Kind kind, String label) {
    }
}
