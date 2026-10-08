package dev.jordy.jordylab.gamecatalog.domain;

/**
 * Which Steam library an entry came from, stored on a {@link GameLibraryEntry}. What a person sees as a game's source
 * ("Steam (Owned)", "Steam (Family)", "Emulated", "Console") is {@link GameSource}, derived on read; there is no stored
 * "local" source.
 */
public enum LibrarySource {
    OWNED,
    FAMILY
}
