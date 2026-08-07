package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SyncRequest(
        @NotNull @Valid SyncSourcePayload source,
        @NotNull @Min(1) Long sequence,
        @NotNull Instant capturedAt,
        boolean scanFailed,
        String scanFailureReason,
        @NotNull @Pattern(regexp = "[0-9a-f]{64}") String payloadHash,
        @NotNull List<@NotNull GamePayload> games) {
}
