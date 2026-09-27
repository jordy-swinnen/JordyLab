package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;

public record HostRef(String hostname, SourceType sourceType) {
}
