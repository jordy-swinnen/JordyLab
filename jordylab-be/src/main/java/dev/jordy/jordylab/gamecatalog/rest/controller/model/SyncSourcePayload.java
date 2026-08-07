package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SyncSourcePayload(
        @NotBlank @Size(max = 100) String sourceKey,
        @NotBlank @Size(max = 500) String path,
        @NotNull SourceType sourceType,
        @NotBlank @Size(max = 50) String platform) {
}
