package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

/**
 * Review list for a Switch bulk add, one entry per distinct non-empty pasted line, in paste order.
 */
public record SwitchBulkPreviewResponse(List<SwitchBulkLine> lines) {
}
