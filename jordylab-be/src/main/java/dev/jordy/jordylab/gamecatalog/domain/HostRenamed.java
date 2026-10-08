package dev.jordy.jordylab.gamecatalog.domain;

import java.util.UUID;

/** Registered by {@link Host#rename(String)}; {@code label} is the name every screen shows from now on. */
public record HostRenamed(UUID hostId, String label) {
}
