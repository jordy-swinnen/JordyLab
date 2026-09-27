package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ChatRequest(
        @NotBlank
        @Size(max = 1000)
        String question,
        List<UUID> gameIds) {
}
