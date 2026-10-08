package dev.jordy.jordylab.gamecatalog.domain;

/**
 * Authority that produced a game's current title (FR-012). A source may only set the title
 * when its authority is at least that of the current one, so a scan never overwrites a
 * library title and vice versa.
 */
public enum TitleSource {
    ROM(1),
    MANIFEST(1),
    LIBRARY(2),
    MANUAL(3);

    private final int authority;

    TitleSource(int authority) {
        this.authority = authority;
    }

    /**
     * Whether a title from this source replaces the current one. A higher authority always does. At equal authority only
     * a library or a person may rename (a Steam name that changed); two scans of equal rank (a ROM file name and a
     * manifest name, from different hosts) keep whichever title the game already has, so the name does not flip with the
     * order the hosts scan in (spec 013 FR-024).
     */
    public boolean replaces(TitleSource current) {
        if (current == null || authority > current.authority) {
            return true;
        }

        return authority == current.authority && (this == LIBRARY || this == MANUAL);
    }

    public boolean outranks(TitleSource other) {
        if (other == null) {
            return true;
        }

        return this.authority >= other.authority;
    }
}
