package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import jakarta.validation.constraints.NotNull;

public record RomStatusRequest(@NotNull RomStatus status) {
}
