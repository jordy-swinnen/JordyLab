package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.UUID;

/** A scanning machine as the Sources page edits it: {@code label} is what everything else shows. */
public record HostResponse(UUID id, String hostname, String displayName, String label) {
}
