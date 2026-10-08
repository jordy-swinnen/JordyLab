package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;

/** Adds a console: a catalog platform or any custom text, and an optional name (defaults to the platform). */
public record ConsoleRequest(@NotBlank String platform, String name) {
}
