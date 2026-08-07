package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

public record IngestConfigResponse(List<SourceEnabledState> sources) {

    public record SourceEnabledState(String sourceKey, boolean enabled) {
    }
}
