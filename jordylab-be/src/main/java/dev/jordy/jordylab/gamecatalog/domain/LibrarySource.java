package dev.jordy.jordylab.gamecatalog.domain;

/**
 * Where a game is available to the user. {@code OWNED} and {@code FAMILY} are stored on a
 * {@link GameLibraryEntry}; {@code LOCAL} is derived-only and means "known only from a scan"
 * (all ROMs and any installed title not present in a synced library).
 */
public enum LibrarySource {
    OWNED,
    FAMILY,
    LOCAL
}
