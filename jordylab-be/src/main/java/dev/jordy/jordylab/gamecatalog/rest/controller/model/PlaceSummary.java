package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

/** The hosts, consoles and platforms the catalog knows (no paths and no machine identifiers). */
public record PlaceSummary(List<String> hosts, List<String> consoles, List<String> platforms) {
}
