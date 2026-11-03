package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogAccess;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The apps the admin can see in the Users panel and, for the grantable ones, give to a person (spec 014 FR-005,
 * data-model.md §5). This enum is the registry: a new app is added here, together with a realm role in Keycloak, and
 * starts as not grantable until it is classified.
 *
 * <p>Deliberately code-only: the panel renders what this enum says, so an app cannot be granted through the API just
 * because someone guessed its key.
 */
public enum ManagedApp {

    GAME_CATALOG(GameCatalogAccess.APP_KEY, "Game Catalog", true, true, "app-gamecatalog",
            "Only people I choose may open it"),

    FNA("fna", "FNA", false, false, null,
            "Holds the admin's private data"),

    SETTINGS("settings", "Settings", false, false, null,
            "Administration only");

    private final String key;
    private final String displayName;
    private final boolean grantable;
    private final boolean defaultGrant;
    private final String roleName;
    private final String notGrantableReason;

    ManagedApp(String key, String displayName, boolean grantable, boolean defaultGrant, String roleName,
            String notGrantableReason) {
        this.key = key;
        this.displayName = displayName;
        this.grantable = grantable;
        this.defaultGrant = defaultGrant;
        this.roleName = roleName;
        this.notGrantableReason = notGrantableReason;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean grantable() {
        return grantable;
    }

    public boolean defaultGrant() {
        return defaultGrant;
    }

    /** The realm role that carries the grant, empty for an app that can never be granted. */
    public Optional<String> grantRole() {
        return Optional.ofNullable(roleName);
    }

    /** The reason shown next to a disabled control, {@code null} for a grantable app. */
    public String notGrantableReason() {
        return grantable ? null : notGrantableReason;
    }

    public static Optional<ManagedApp> fromKey(String key) {
        return Arrays.stream(values())
                .filter(app -> app.key.equals(key))
                .findFirst();
    }

    /** The grants a newly approved person starts with (FR-008). */
    public static List<ManagedApp> defaultGrants() {
        return Arrays.stream(values())
                .filter(ManagedApp::defaultGrant)
                .toList();
    }
}
