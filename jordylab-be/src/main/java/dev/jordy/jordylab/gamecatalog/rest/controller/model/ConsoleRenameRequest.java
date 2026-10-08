package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;

public record ConsoleRenameRequest(@NotBlank String name) {
}
