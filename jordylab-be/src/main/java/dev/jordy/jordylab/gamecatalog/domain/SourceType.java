package dev.jordy.jordylab.gamecatalog.domain;

public enum SourceType {

    STEAM {
        @Override
        public String platform() {
            return "Steam";
        }
    },
    EMUDECK {
        @Override
        public String platform() {
            return "EmuDeck";
        }
    };

    /**
     * Default platform name associated with this source type. Used as the
     * scan_source.platform column default and as the value applied to
     * individual {@code game.platform} records that don't carry their own
     * per-emulator override.
     */
    public abstract String platform();
}
