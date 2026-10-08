package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

public record ConsoleBulkSummaryResponse(List<ConsoleGameResponse> added, List<String> skipped, List<Failure> failed) {

    public record Failure(String line, String reason) {
    }
}
