package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

/** The review of a pasted list: nothing is saved yet. */
public record ConsoleBulkPreviewResponse(List<Line> lines) {

    public enum Status {
        MATCHED,
        NO_MATCH,
        ALREADY_PRESENT
    }

    public record Line(String line, Status status, ConsoleSearchResult match) {
    }
}
