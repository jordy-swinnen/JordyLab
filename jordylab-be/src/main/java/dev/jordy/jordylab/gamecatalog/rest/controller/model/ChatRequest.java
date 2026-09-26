package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank
        @Size(max = 1000)
        String question) {
}
