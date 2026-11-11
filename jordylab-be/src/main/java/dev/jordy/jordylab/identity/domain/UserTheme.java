package dev.jordy.jordylab.identity.domain;

/**
 * The account-wide theme choice (spec 024 US3). The ids match the six themes in
 * {@code jordylab-fe/themes.js}; {@link #SYSTEM} follows the device, matching the
 * web app's existing {@code system} value.
 */
public enum UserTheme {

    CONTENT("content"),
    OXBLOOD("oxblood"),
    FLARE("flare"),
    PAPER("paper"),
    GLASSHOUSE("glasshouse"),
    HEATHER("heather"),
    SYSTEM("system");

    private final String key;

    UserTheme(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static UserTheme fromKey(String value) {
        if (value == null) {
            return SYSTEM;
        }
        for (UserTheme theme : values()) {
            if (theme.key.equalsIgnoreCase(value.trim())) {
                return theme;
            }
        }

        return SYSTEM;
    }
}
