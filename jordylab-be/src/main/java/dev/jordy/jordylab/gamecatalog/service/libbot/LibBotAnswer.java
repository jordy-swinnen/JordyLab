package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** What the second model call writes: the answer and the ids of the games it recommends, from the given rows only. */
public record LibBotAnswer(
        @NotBlank @Size(max = 3000) @JsonPropertyDescription("The answer, in the person's language") String text,
        @JsonPropertyDescription("Ids of the games named in the answer, taken from the given rows") List<UUID> recommendedGameIds) {

    public LibBotAnswer {
        recommendedGameIds = recommendedGameIds == null ? List.of() : List.copyOf(recommendedGameIds);
    }
}
