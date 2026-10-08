package dev.jordy.jordylab.gamecatalog.domain;

/** The four labels a game can carry about where it comes from (spec 013 FR-039). Derived, never stored. */
public enum GameSource {
    STEAM_OWNED("Steam (Owned)"),
    STEAM_FAMILY("Steam (Family)"),
    EMULATED("Emulated"),
    CONSOLE("Console");

    private final String label;

    GameSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
