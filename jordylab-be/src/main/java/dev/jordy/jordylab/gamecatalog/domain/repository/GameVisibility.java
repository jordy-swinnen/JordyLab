package dev.jordy.jordylab.gamecatalog.domain.repository;

/**
 * The one definition of "a game is visible" (spec 013 data-model "Visibility predicate"), as JPQL fragments for
 * {@code @Query} strings that select a {@code Game g}: an installed copy on an enabled source, OR an active Steam library
 * entry, OR a console entry. Disabling a source therefore hides games that live only there and nothing else (FR-029).
 * The grid, the detail page, the platform and place lists and LibBot's retrieval all use these constants instead of
 * repeating the rule.
 */
public final class GameVisibility {

    /** An installed copy on an enabled source. */
    public static final String INSTALLED_COPY = "EXISTS (SELECT 1 FROM GameInstallation vgi WHERE vgi.game = g "
            + "AND vgi.presence = 'INSTALLED' AND vgi.source.enabled = true)";

    /** An active (not removed) Steam library entry, owned or family. */
    public static final String ACTIVE_LIBRARY_ENTRY = "EXISTS (SELECT 1 FROM GameLibraryEntry vle WHERE vle.game = g "
            + "AND vle.removedAt IS NULL)";

    /** The game is on at least one console. */
    public static final String CONSOLE_ENTRY = "EXISTS (SELECT 1 FROM ConsoleGameEntry vce WHERE vce.game = g)";

    /** The full rule. */
    public static final String VISIBLE = "(" + INSTALLED_COPY + " OR " + ACTIVE_LIBRARY_ENTRY + " OR " + CONSOLE_ENTRY + ")";

    /** Playable right now: an installed copy on an enabled source or a console (library-only games are not). */
    public static final String AVAILABLE_NOW = "(" + INSTALLED_COPY + " OR " + CONSOLE_ENTRY + ")";

    private GameVisibility() {
    }
}
