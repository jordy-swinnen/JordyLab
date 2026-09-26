package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/gamecatalog/ingest/check} — the scan client asks whether a
 * scan is needed before uploading anything. The client supplies an opaque
 * metadata fingerprint; the server decides, using its own stored state, whether
 * a scan is actually required.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanCheckRequest(
        @Size(max = 100) String machineId,
        @NotBlank @Size(max = 100) String hostname,
        @NotNull SourceType libraryType,
        @Size(max = 200) String clientDigest) {
}
