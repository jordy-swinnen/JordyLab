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

    public boolean outranks(TitleSource other) {
        if (other == null) {
            return true;
        }

        return this.authority >= other.authority;
    }
}
