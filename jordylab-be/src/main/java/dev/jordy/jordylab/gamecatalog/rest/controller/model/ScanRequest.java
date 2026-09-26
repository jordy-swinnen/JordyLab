package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The payload sent by the downloaded scan script. The backend parses the
 * directory listing ({@code paths}) and, for Steam, the included
 * {@code manifestContents} (raw VDF text per {@code appmanifest_<appid>.acf})
 * into a list of {@link GamePayload} records before reconciling.
 *
 * <p>The script reads the host's {@code hostname} and includes it here; the
 * backend auto-registers a {@code ScanSource} keyed on
 * {@code (hostname, libraryType)} if one doesn't exist yet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanRequest(
        @NotBlank @Size(max = 100) String hostname,
        @NotNull SourceType libraryType,
        @NotNull Instant capturedAt,
        @NotNull List<@NotNull ScanEntry> paths,
        Map<String, String> manifestContents) {
}
