package dev.jordy.jordylab.gamecatalog;

import lombok.experimental.UtilityClass;

/**
 * The Game Catalog's public identity as an app grant (spec 014). {@code settings} owns the panel that grants apps and
 * already depends on {@code gamecatalog}, so it reads this constant from the module's root package — the public API.
 * Nothing in {@code gamecatalog} may read {@code settings}: that would be a module cycle.
 */
@UtilityClass
public class GameCatalogAccess {

    /** Stable key shared by the event contract and the app registry. */
    public static final String APP_KEY = "gamecatalog";
}
