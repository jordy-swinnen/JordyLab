package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

/**
 * Summary of a confirmed Switch bulk add: added, already present and skipped lines (spec 009 US3 AS2).
 */
public record SwitchBulkConfirmResponse(List<SwitchGameResponse> added, List<String> alreadyPresent,
        List<Skipped> skipped) {

    public record Skipped(String line, String reason) {
    }
}
