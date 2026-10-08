package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConsoleBulkPreviewRequest(@NotNull List<String> lines) {
}
