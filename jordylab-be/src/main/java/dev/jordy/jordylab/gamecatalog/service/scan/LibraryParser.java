package dev.jordy.jordylab.gamecatalog.service.scan;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;

import java.util.List;
import java.util.Map;

/**
 * Parses a script-sent directory listing (and any per-source VDF contents)
 * into a flat list of {@link GamePayload} records that the existing
 * {@code ReconciliationService} can diff against the current games table.
 */
public interface LibraryParser {

    /**
     * The {@link SourceType} this parser handles. The dispatch in
     * {@code ScanService} looks up the parser by source type.
     */
    SourceType supports();

    /**
     * Parse the listing for the given library type.
     *
     * @param request the full {@link ScanRequest} including hostname, library type,
     *                and the captured listing.
     * @return the parsed game payloads. May be empty if nothing matched.
     */
    List<GamePayload> parse(ScanRequest request);

    /**
     * Convenience accessor for the listing portion of the request.
     */
    default List<ScanEntry> entries(ScanRequest request) {
        return request.paths();
    }

    /**
     * Convenience accessor for the per-source manifest contents map
     * (Steam VDFs, etc.). May be {@code null} or empty.
     */
    default Map<String, String> manifests(ScanRequest request) {
        return request.manifestContents();
    }
}
