package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotNull;

public record SetSourceEnabledRequest(@NotNull Boolean enabled) {
}
