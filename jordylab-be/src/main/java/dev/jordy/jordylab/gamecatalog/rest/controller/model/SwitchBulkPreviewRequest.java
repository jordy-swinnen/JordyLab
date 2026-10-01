package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Pasted list for a Switch bulk add: one title per line (spec 009 US3).
 */
public record SwitchBulkPreviewRequest(@NotBlank String text) {
}
