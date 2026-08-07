package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScanEntry(
        @NotBlank String relpath,
        long size,
        @NotNull Instant mtime) {
}
