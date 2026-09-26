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
 * The payload sent by the scan client. The backend parses the directory
 * listing ({@code paths}) and, for Steam, the included {@code manifestContents}
 * (raw VDF text per {@code appmanifest_<appid>.acf}). For EmuDeck the client may
 * instead send its own {@code games} (already grouped and normalized on the
 * host); when present, those are the parsed game set.
 *
 * <p>The client also sends a stable {@code machineId} and an opaque
 * {@code clientDigest} used only for the {@code /check} skip hint, and may set
 * {@code force} to bypass the server-side shrink guard.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanRequest(
        @Size(max = 100) String machineId,
        @NotBlank @Size(max = 100) String hostname,
        @NotNull SourceType libraryType,
        @NotNull Instant capturedAt,
        @Size(max = 200) String clientDigest,
        Boolean force,
        @NotNull List<@NotNull ScanEntry> paths,
        Map<String, String> manifestContents,
        List<@NotNull ClientGame> games) {
}
